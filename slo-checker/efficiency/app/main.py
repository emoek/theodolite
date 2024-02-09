from fastapi import FastAPI,Request
import logging
import os
import json
import sys
import re
import pandas as pd
import numpy as np


app = FastAPI()
app.state.counter = 0
logging.basicConfig(stream=sys.stdout,
                    format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("API")



if os.getenv('LOG_LEVEL') == 'INFO':
    logger.setLevel(logging.INFO)
elif os.getenv('LOG_LEVEL') == 'WARNING':
    logger.setLevel(logging.WARNING)
elif os.getenv('LOG_LEVEL') == 'DEBUG':
    logger.setLevel(logging.DEBUG)


def get_aggr_func(func_string: str):
    if func_string in ['mean', 'median', 'mode', 'sum', 'count', 'max', 'min', 'std', 'var', 'skew', 'kurt']:
        return func_string
    elif func_string == 'first':
        def first(x):
            return x.iloc[0]
        first.__name__ = 'first'
        return first
    elif func_string == 'last':
        def last(x):
            return x.iloc[-1]
        last.__name__ = 'last'
        return last
    elif func_string == 'geomean':
        def geomean(x):
            return np.exp(np.mean(np.log(x[x > 0])))  # Ensure positive values only
        geomean.__name__ = 'geomean'
        return geomean
    elif re.search(r'^p\d\d?(\.\d+)?$', func_string): # matches strings like 'p99', 'p99.99', 'p1', 'p0.001'
        def percentile(x):
            return x.quantile(float(func_string[1:]) / 100)
        percentile.__name__ = func_string
        return percentile
    else:
        raise ValueError('Invalid function string.')

def aggr_query(values: dict, warmup: int, aggr_func):

    df = pd.DataFrame.from_dict(values)
    df.columns = ['timestamp', 'value']
    filtered = df[df['timestamp'] >= (df['timestamp'][0] + warmup)]
    filtered['value'] = filtered['value'].astype(float)
    return filtered['value'].aggregate(aggr_func)





def check_result(result, operator: str, threshold):
    if operator == 'lt':
        return result < threshold
    if operator == 'lte':
        return result <= threshold
    if operator == 'gt':
        return result > threshold
    if operator == 'gte':
        return result >= threshold
    if operator == 'true':
        return True # Mainly used for testing
    if operator == 'false':
        return False # Mainly used for testing
    else:
        raise ValueError('Invalid operator string.')
    

# global count 
# count = 0


global gload 
gload = 0
all_results_data = {}
def update_results_data(type_key, data):
    all_results_data[type_key] = data
    # Optionally, save each update to disk


def calcStagedConsumption(baseConsData, loadConsData, metadata):
    
    warmup = int(metadata['warmup'])
    query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])

    baseConsQueryResults = [aggr_query(r, warmup, query_aggregation) for r in baseConsData]
    loadConsQueryResults = [aggr_query(r, warmup, query_aggregation) for r in loadConsData]

    logger.info("C Base: %d", baseConsQueryResults)
    update_results_data("C_Base", baseConsQueryResults)
    logger.info("C Load: %d", loadConsQueryResults)
    update_results_data("C_Load", loadConsQueryResults)

    baseResult = pd.DataFrame(baseConsQueryResults).aggregate(rep_aggregation).at[0]
    loadResult = pd.DataFrame(loadConsQueryResults).aggregate(rep_aggregation).at[0]
    logger.info("C Base over repetition: %d", baseResult)
    update_results_data("C_Base_r", baseResult)
    update_results_data("C_Load_r", loadResult)

    logger.info("C Load over repetition: %d", loadResult)


    result = loadResult - baseResult

    return result


def calcConsumption(loadConsData, metadata):
    
    warmup = int(metadata['warmup'])
    query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    # operator = metadata['operator']
    # threshold = float(metadata['threshold'])

    loadConsQueryResults = [aggr_query(r, warmup, query_aggregation) for r in loadConsData]
    logger.info("C Load: %d", loadConsQueryResults)
    update_results_data("C_Load", loadConsQueryResults)

    loadResult = pd.DataFrame(loadConsQueryResults).aggregate(rep_aggregation).at[0]
    logger.info("C Load over repetition: %d", loadResult)
    update_results_data("C_Load_r", loadResult)
    result = loadResult

    return result


def calcStagedWorkloadMetric(idleWorkloadData, loadWorkloadData, metadata):
    
    warmup = int(metadata['warmup'])
    query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    # operator = metadata['operator']
    # threshold = float(metadata['threshold'])

    idleWMQueryResults = [aggr_query(r, warmup, query_aggregation) for r in idleWorkloadData]
    loadWMQueryResults = [aggr_query(r, warmup, query_aggregation) for r in loadWorkloadData]

    logger.info("WMQ Idle: %d", idleWMQueryResults)
    update_results_data("WMQ_Idle", idleWMQueryResults)
    logger.info("WMQ Load: %d", loadWMQueryResults)
    update_results_data("WMQ_Load", loadWMQueryResults)
    idleResult = pd.DataFrame(idleWMQueryResults).aggregate(rep_aggregation).at[0]
    loadResult = pd.DataFrame(loadWMQueryResults).aggregate(rep_aggregation).at[0]
    logger.info("WMQ Idle over repetition: %d", idleResult)
    update_results_data("WMQ_Idle_r", idleResult)
    logger.info("WMQ Load over repetition: %d", loadResult)
    update_results_data("WMQ_Load_r", loadResult)
    result = loadResult - idleResult

    return result


def calcWorkloadMetric(loadWorkloadData, metadata):
    
    warmup = int(metadata['warmup'])
    query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    # operator = metadata['operator']
    # threshold = float(metadata['threshold'])

    loadWMQueryResults = [aggr_query(r, warmup, query_aggregation) for r in loadWorkloadData]
    logger.info("WMQ Load: %d", loadWMQueryResults)
    update_results_data("WMQ_Load", loadWMQueryResults)
    loadResult = pd.DataFrame(loadWMQueryResults).aggregate(rep_aggregation).at[0]
    logger.info("WMQ Load over repetition: %d", loadResult)
    update_results_data("WMQ_Load_r", loadResult)

    result = loadResult

    return result


def calcStagedWorkloadLog(idleWorkloadData, loadWorkloadData, metadata):
    


    nrIdleLogs = [len(r) for r in idleWorkloadData]

    nrLoadLogs = [len(r) for r in loadWorkloadData]
    logger.info("WLQ Idle: %d", nrIdleLogs)
    update_results_data("WLQ_Idle", nrIdleLogs)
    logger.info("WLQ Load: %d", nrLoadLogs)
    update_results_data("WLQ_Load", nrLoadLogs)

    logIdleResult = pd.DataFrame(nrIdleLogs).aggregate('mean').at[0]
    logLoadResult = pd.DataFrame(nrLoadLogs).aggregate('mean').at[0]
    logger.info("WLQ Idle over repetitions: %d", logIdleResult)
    update_results_data("WLQ_Idle_r", logIdleResult)
    logger.info("WLQ Load over repetitions: %d", logLoadResult)
    update_results_data("WLQ_Load_r", logLoadResult)


    result = logLoadResult - logIdleResult

    return result


def calcWorkloadLog(loadWorkloadData, metadata):
    
   
    nrLoadLogs = [len(r) for r in loadWorkloadData]
    logger.info("WLQ Load: %d", nrLoadLogs)
    update_results_data("WLQ_Load", nrLoadLogs)
    logResult = pd.DataFrame(nrLoadLogs).aggregate('mean').at[0]
    logger.info("WLQ Load over repetitions: %d", logResult)
    update_results_data("WLQ_Load_r", logResult)


    result = logResult

    return result













@app.post("/type1",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad



    baseCons = []
    loadCons = []
    idleLogs = []
    loadLogs = []
    resultWLQ = 1
    resultCons = 1

    isRawLogs = False
    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])



        
        if entry["second"]["third"]:
            if entry["second"]["third"][0]["stream"] != None:
                isRawLogs = True 
                idleLogs.append(entry["second"]["third"][0]["values"])
            else:
                idleLogs.append(entry["second"]["third"][0]["values"][-1][1])

        # Extract log values if present
        if entry["third"]["third"]:
            if entry["third"]["third"][0]["stream"] != None:
                isRawLogs = True 


                loadLogs.append(entry["third"]["third"][0]["values"])
            else:
                loadLogs.append(entry["third"]["third"][0]["values"][-1][1])





    if isRawLogs:
        resultWLQ = calcStagedWorkloadLog(idleLogs, loadLogs, metadata)
    else:
        idleLogs = [int(item) for item in idleLogs]

        loadLogs = [int(item) for item in loadLogs]

        idleLogs_r = (pd.DataFrame(idleLogs).aggregate('mean').at[0])
        loadLogs_r = (pd.DataFrame(loadLogs).aggregate('mean').at[0])

        resultWLQ = (loadLogs_r) - (idleLogs_r)

        update_results_data("WLQ_Idle", idleLogs)
        update_results_data("WLQ_Load", loadLogs)
        update_results_data("WLQ_Idle_r", idleLogs_r)
        update_results_data("WLQ_Load_r", loadLogs_r)






    # values = data["results"]
    # resultLoad = values['second']
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWLQ / resultCons


    logger.info("WLQ result: %d", resultWLQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type1_results.json', {
        'WLQ_result': resultWLQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)

    return check_result(result, operator, threshold)








@app.post("/type2",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad


    loadCons = []
    idleLogs = []
    loadLogs = []
    resultWLQ = 1
    resultCons = 1

    isRawLogs = False
    
    for entry in data["results"]["first"]:


        

        loadCons.append(entry["third"]["second"][0]["values"])




        if entry["second"]["third"]:
            if entry["second"]["third"][0]["stream"] != None:
                isRawLogs = True 
                idleLogs.append(entry["second"]["third"][0]["values"])
            else:
                idleLogs.append(entry["second"]["third"][0]["values"][-1][1])

        # Extract log values if present
        if entry["third"]["third"]:
            if entry["third"]["third"][0]["stream"] != None:
                isRawLogs = True
                loadLogs.append(entry["third"]["third"][0]["values"])
            else:
                loadLogs.append(entry["third"]["third"][0]["values"][-1][1])


    if isRawLogs:
        resultWLQ = calcStagedWorkloadLog(idleLogs, loadLogs, metadata)
    else:
        idleLogs = [int(item) for item in idleLogs]

        loadLogs = [int(item) for item in loadLogs]
        
        idleLogs_r = (pd.DataFrame(idleLogs).aggregate('mean').at[0])
        loadLogs_r = (pd.DataFrame(loadLogs).aggregate('mean').at[0])
        resultWLQ = (loadLogs_r) - (idleLogs_r)

        update_results_data("WLQ_Idle", idleLogs)
        update_results_data("WLQ_Load", loadLogs)
        update_results_data("WLQ_Idle_r", idleLogs_r)
        update_results_data("WLQ_Load_r", loadLogs_r)


    # values = data["results"]
    # resultLoad = values['second']
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWLQ / resultCons

    logger.info("WLQ result: %d", resultWLQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type2_results.json', {
        'WLQ_result': resultWLQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)

    return check_result(result, operator, threshold)






@app.post("/type3",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])

    app.state.counter += 1

    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad
    



    baseCons = []
    loadCons = []
    loadLogs = []
    resultWLQ = 1
    resultCons = 1

    isRawLogs = False
 
    for entry in data["results"]["first"]:

        baseCons.append(entry["first"]["second"][0]["values"])
   
        loadCons.append(entry["third"]["second"][0]["values"])

        if entry["third"]["third"]:
            if entry["third"]["third"][0]["stream"] != None:
                isRawLogs = True
                loadLogs.append(entry["third"]["third"][0]["values"])

            else:
                loadLogs.append(entry["third"]["third"][0]["values"][-1][1])




    if isRawLogs:
        resultWLQ = calcWorkloadLog(loadLogs, metadata)
    else:
        loadLogs = [int(item) for item in loadLogs]
        
        resultWLQ = (pd.DataFrame(loadLogs).aggregate('mean').at[0])

        update_results_data("WLQ_Load", loadLogs)
        update_results_data("WLQ_Load_r", resultWLQ)


    # values = data["results"]
    # resultLoad = values['second']
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWLQ / resultCons
    logger.info("WLQ result: %d", resultWLQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type3_results.json', {
        'WLQ_result': resultWLQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)









@app.post("/type4",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad


    loadCons = []
    loadLogs = []
    resultWLQ = 1
    resultCons = 1

    isStaged = False
    isRawLogs = False


    if len(data["results"]["first"]) > 2:
        isStaged = True
        for entry in data["results"]["first"]:


            

            loadCons.append(entry["third"]["second"][0]["values"])



            if entry["third"]["third"]:
                if entry["third"]["third"][0]["stream"]:
                    loadLogs.append(entry["third"]["third"][0]["values"])
                    print("raw logs")
                    isRawLogs = True
                else:
                    print("No raw logs")
                    loadLogs.append(entry["third"]["third"][0]["values"][-1][1])
    else:
        for entry in data["results"]["first"]["first"]:

            loadCons.append(entry[0]["values"])

            

        for entry in data["results"]["first"]["second"]:
            if entry[0]["stream"] != None:
                loadLogs.append(entry[0]["values"])
                print("raw logs")
                isRawLogs = True

            else:
                
                loadLogs.append(entry[0]["values"][-1][1])
                print("No raw logs")



    if isRawLogs:
        resultWLQ = calcWorkloadLog(loadLogs, metadata)
    else:
        loadLogs = [int(item) for item in loadLogs]
        resultWLQ = (pd.DataFrame(loadLogs).aggregate('mean').at[0])
        update_results_data("WLQ_Load", loadLogs)
        update_results_data("WLQ_Load_r", resultWLQ)


    # values = data["results"]
    # resultLoad = values['second']
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWLQ / resultCons
    logger.info("WLQ result: %d", resultWLQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type4_results.json', {
        'WLQ_result': resultWLQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)

    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)








@app.post("/type5",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad

    baseCons = []
    loadCons = []
    idleLogs = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])




        if entry["second"]["third"]:
            idleLogs.append(entry["second"]["third"][0]["values"])

        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])



    # values = data["results"]
    # resultLoad = values['second']
    resultWMQ = calcStagedWorkloadMetric(idleLogs, loadLogs, metadata)
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWMQ / resultCons

    logger.info("WLQ result: %d", resultWMQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type5_results.json', {
        'WMQ_result': resultWMQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)









@app.post("/type6",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad



    loadCons = []
    idleLogs = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        

        loadCons.append(entry["third"]["second"][0]["values"])




        if entry["second"]["third"]:
            idleLogs.append(entry["second"]["third"][0]["values"])

        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])

    # values = data["results"]
    # resultLoad = values['second']
    resultWMQ = calcStagedWorkloadMetric(idleLogs, loadLogs, metadata)
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWMQ / resultCons

    logger.info("WLQ result: %d", resultWMQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type6_results.json', {
        'WMQ_result': resultWMQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)





@app.post("/type7",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad



    baseCons = []
    loadCons = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])


        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])



    # values = data["results"]
    # resultLoad = values['second']
    resultWMQ = calcWorkloadMetric(loadLogs, metadata)
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWMQ / resultCons

    logger.info("WLQ result: %d", resultWMQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type7_results.json', {
        'WMQ_result': resultWMQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)





@app.post("/type8",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad


    loadCons = []
    loadLogs = []

    if len(data["results"]["first"]) > 2:

        for entry in data["results"]["first"]:


            

            loadCons.append(entry["third"]["second"][0]["values"])




            if entry["third"]["third"]:
                loadLogs.append(entry["third"]["third"][0]["values"])
    else:
        for entry in data["results"]["first"]["first"]:

            loadCons.append(entry[0]["values"])

            

        for entry in data["results"]["first"]["second"]:


            loadLogs.append(entry[0]["values"])
        
    # values = data["results"]

    # resultLoad = values['second']
    resultWMQ = calcWorkloadMetric(loadLogs, metadata)
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWMQ / resultCons

    logger.info("WMQ result: %d", resultWMQ)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type8_results.json', {
        'WMQ_result': resultWMQ,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)







@app.post("/type9",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad




    baseCons = []
    loadCons = []


    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])




    # values = data["results"]




    # resultLoad = values['second']
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultLoad / resultCons

    logger.info("Load: %d", resultLoad)
    logger.info("C result: %d", resultCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type9_results.json', {
        'Load': resultLoad,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, resultLoad)
    return check_result(result, operator, threshold)





@app.post("/type10",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad



    loadCons = []


    if len(data["results"]["first"]) > 2:

        for entry in data["results"]["first"]:


            

            loadCons.append(entry["third"]["second"][0]["values"])
    else:
        for entry in data["results"]["first"]["first"]:

            loadCons.append(entry[0]["values"])

            




         





    # values = data["results"]



    # resultLoad = values['second']
    resultCons = calcConsumption(loadCons, metadata)

    result = resultLoad / resultCons

    logger.info("Load: %d", resultLoad)
    logger.info("C Load result: %d", resultCons)
    logger.info("Efficiency result: %d", result)
    save_data_to_json('type10_results.json', {
        'Load': resultLoad,
        'C_result': resultCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)






@app.post("/type11",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad

    baseCons = []
    loadCons = []


    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])


    # values = data["results"]
    # resultLoad = values['second']

    resultBaseCons = calcConsumption(baseCons, metadata)
    resultLoadCons = calcConsumption(loadCons, metadata)

    result = resultBaseCons / resultLoadCons


    logger.info("C Base result: %d", resultBaseCons)
    logger.info("C Load result: %d", resultLoadCons)
    logger.info("Efficiency result: %d", result)

    save_data_to_json('type11_results.json', {
        'C_Base_result': resultBaseCons,
        'C_Load_result': resultLoadCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)








@app.post("/type12",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1

    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])
    values = data["results"]
    resultLoad = values['second']
    gload = resultLoad



    idleCons = []
    loadCons = []

    
    for entry in data["results"]["first"]:


        idleCons.append(entry["second"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])

    # values = data["results"]
    # resultLoad = values['second']

    resultIdleCons = calcConsumption(idleCons, metadata)
    resultLoadCons = calcConsumption(loadCons, metadata)

    result = resultIdleCons / resultLoadCons

    logger.info("C Idle result: %d", resultIdleCons)
    logger.info("C Load result: %d", resultLoadCons)
    logger.info("Efficiency result: %d", result)
    save_data_to_json('type12_results.json', {
        'C_Idle_result': resultIdleCons,
        'C_Load_result': resultLoadCons,
        'Efficiency_result': result
    }, gload)
    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(result, operator, threshold)





# def save_data_to_json(filename, data):
#     if not os.path.exists("slo_results"):
#         os.mkdir("slo_results")
#     path = os.path.join("slo_results", filename)
#     with open(path, 'w') as file:
#         json.dump(data, file, indent=4)


def save_data_to_json(filename, data, load):
    load = str(load)
    if not os.path.exists("slo_results"):
        os.mkdir("slo_results")

    resFol = "slo_results" + "_" + str(app.state.counter) + "_" + str(load) 

    path = os.path.join("slo_results", resFol)
    if not os.path.exists(path):
        os.mkdir(path)

    file = os.path.join(path, filename)
    with open(file, 'w') as file:
        json.dump(data, file, indent=4)










@app.post("/",response_model=bool)
async def check_slo(request: Request):
    logger.error('Please provide a SLO Type for Efficiency Benchmarks')

    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)

    return False


logger.info("SLO evaluator is online")