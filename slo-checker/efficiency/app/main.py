from fastapi import FastAPI,Request
import logging
import os
import json
import sys
import re
import pandas as pd
import numpy as np
from collections import defaultdict

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
    elif func_string == 'len':
        def len(x):
            return len(x)
        len.__name__ = 'len'
        return len
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
    # query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    query_aggregation = get_aggr_func(metadata['workloadAggregation'])

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
    # query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    query_aggregation = get_aggr_func(metadata['workloadAggregation'])
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
    

    query_aggregation = get_aggr_func(metadata['workloadAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    nrIdleLogs = [len(r) for r in idleWorkloadData]

    nrLoadLogs = [len(r) for r in loadWorkloadData]
    logger.info("WLQ Idle: %d", nrIdleLogs)
    update_results_data("WLQ_Idle", nrIdleLogs)
    logger.info("WLQ Load: %d", nrLoadLogs)
    update_results_data("WLQ_Load", nrLoadLogs)

    logIdleResult = pd.DataFrame(nrIdleLogs).aggregate(rep_aggregation).at[0]
    # logLoadResult = pd.DataFrame(nrLoadLogs).aggregate('mean').at[0]
    logLoadResult = pd.DataFrame(nrLoadLogs).aggregate(rep_aggregation).at[0]

    logger.info("WLQ Idle over repetitions: %d", logIdleResult)
    update_results_data("WLQ_Idle_r", logIdleResult)
    logger.info("WLQ Load over repetitions: %d", logLoadResult)
    update_results_data("WLQ_Load_r", logLoadResult)


    result = logLoadResult - logIdleResult

    return result


def calcWorkloadLog(loadWorkloadData, metadata):
    warmup = int(metadata['warmup'])
    # query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])

    query_aggregation = get_aggr_func(metadata['workloadAggregation'])


    # maxLoadLogTest = [aggr_query(r, warmup, "max") for r in loadWorkloadData]

   
    nrLoadLogs = [len(r) for r in loadWorkloadData]


    logger.info("WLQ Load: %d", nrLoadLogs)
    update_results_data("WLQ_Load", nrLoadLogs)
    logResult = pd.DataFrame(nrLoadLogs).aggregate(rep_aggregation).at[0]
    logger.info("WLQ Load over repetitions: %d", logResult)
    update_results_data("WLQ_Load_r", logResult)


    result = logResult

    return result





def calcLogBasedEfficiency(loadCons, loadLogs, type_param, metadata):
    
    warmup = int(metadata['warmup'])
    # query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    total_results = []
    final_result = 0


    if type_param == 2:

        for (load, log) in zip(loadCons, loadLogs):
            log = {item[0]: item[1] for item in log }
            results = []
            for timestamp, consumption in load.items():
                throughput = float(log.get(timestamp, 0))
                consumption = float(consumption)
                if consumption > 0:
                    results.append(throughput / consumption)
            
            results = np.array(results)
            geomean = np.exp(np.mean(np.log(results[results > 0])))
            total_results.append(geomean)
                
    else:

        for (load, log) in zip(loadCons, loadLogs):
            results = []

            for i in range(0,len(log)-1):
                if float(load[log[i][0]]) > 0:
                    efficiency = log[i][1] / float(load[log[i][0]])



                    results.append(efficiency)
                

            results = np.array(results)
            geomean = np.exp(np.mean(np.log(results[results > 0])))
            total_results.append(geomean)
        
    
    # final_result = np.mean(total_results)
    final_result = pd.DataFrame(total_results).aggregate(rep_aggregation).at[0]
    logger.info("Efficiency Ratio Results: %d", total_results)
    update_results_data("ERR", total_results)

    logger.info("Efficiency Ratio Results over repetitions: %d", final_result)
    update_results_data("ERR_r", final_result)





    return final_result

















@app.post("/type1",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    app.state.counter += 1


    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
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
                max_value = max([int(value[1]) for value in entry["third"]["third"][0]["values"]])
                loadLogs.append(max_value)
                # loadLogs.append(entry["third"]["third"][0]["values"][-1][1])





    if isRawLogs:
        resultWLQ = calcStagedWorkloadLog(idleLogs, loadLogs, metadata)
    else:
        idleLogs = [int(item) for item in idleLogs]

        loadLogs = [int(item) for item in loadLogs]

        idleLogs_r = (pd.DataFrame(idleLogs).aggregate(rep_aggregation).at[0])
        # loadLogs_r = (pd.DataFrame(loadLogs).aggregate('mean').at[0])
        loadLogs_r = (pd.DataFrame(loadLogs).aggregate(rep_aggregation).at[0])


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
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
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

                max_value = max([int(value[1]) for value in entry["third"]["third"][0]["values"]])
                loadLogs.append(max_value)
                # loadLogs.append(entry["third"]["third"][0]["values"][-1][1])


    if isRawLogs:
        resultWLQ = calcStagedWorkloadLog(idleLogs, loadLogs, metadata)
    else:
        idleLogs = [int(item) for item in idleLogs]

        loadLogs = [int(item) for item in loadLogs]
        
        idleLogs_r = (pd.DataFrame(idleLogs).aggregate(rep_aggregation).at[0])
        loadLogs_r = (pd.DataFrame(loadLogs).aggregate(rep_aggregation).at[0])
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
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    operator = metadata['operator']
    warmup = metadata['warmup']
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
    
                max_value = max([int(value[1]) for value in entry["third"]["third"][0]["values"]])
                loadLogs.append(max_value)
                
                # loadLogs.append(entry["third"]["third"][0]["values"][-1][1])



    if isRawLogs:
        resultWLQ = calcWorkloadLog(loadLogs, metadata)
    else:
        loadLogs = [int(item) for item in loadLogs]
        
        #Repetitions
        resultWLQ = (pd.DataFrame(loadLogs).aggregate(rep_aggregation).at[0])

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
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
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


    # if len(data["results"]["first"][0]) > 2:
    if isinstance(data["results"]["first"], list):
        isStaged = True
        for entry in data["results"]["first"]:


            

            loadCons.append(entry["third"]["second"][0]["values"])



            if entry["third"]["third"]:
                if entry["third"]["third"][0]["stream"]:
                    loadLogs.append(entry["third"]["third"][0]["values"])
                    isRawLogs = True
                else:
                    max_value = max([int(value[1]) for value in entry["third"]["third"][0]["values"]])
                    loadLogs.append(max_value)
                    # loadLogs.append(entry["third"]["third"][0]["values"][-1][1])
    else:
        for entry in data["results"]["first"]["first"]:

            loadCons.append(entry[0]["values"])

            

        for entry in data["results"]["first"]["second"]:
            if entry[0]["stream"] != None:
                loadLogs.append(entry[0]["values"])
                isRawLogs = True

            else:
                max_value = max([int(value[1]) for value in entry[0]["values"]])
                loadLogs.append(max_value)
                # loadLogs.append(entry[0]["values"][-1][1])



    if isRawLogs:
        resultWLQ = calcWorkloadLog(loadLogs, metadata)
    else:
        loadLogs = [int(item) for item in loadLogs]
        resultWLQ = (pd.DataFrame(loadLogs).aggregate(rep_aggregation).at[0])
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

    # if len(data["results"]["first"][0]) > 2:
    if isinstance(data["results"]["first"], list):
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


    # if len(data["results"]["first"]) > 2:
    if isinstance(data["results"]["first"], list):
        for entry in data["results"]["first"]:


            

            loadCons.append(entry["third"]["second"][0]["values"])
    else:
        for entry in data["results"]["first"]["first"]:

            loadCons.append(entry[0]["values"])


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





@app.post("/type13/{type_param}",response_model=bool)
async def check_slo(type_param: int, request: Request):
    if type_param != 2:
        type_param = 1

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

    ## dicts in list
    loadCons = []

    ## lists in list
    loadLogs = []
    resultWLQ = 1
    resultCons = 1

    isStaged = False
    isRawLogs = False

    

    ## if stagebased, else nonisolated/original
    # if len(data["results"]["first"]) > 2:
    if isinstance(data["results"]["first"], list):
        isStaged = True
        counter = 0
        for entry in data["results"]["first"]:
            mapped_values = {}

            ## load stage cons
            loadCons_dict = {item[0]: item[1] for item in entry["third"]["second"][0]["values"] }

            loadCons.append(loadCons_dict)

            

            ## load stage logs 
            if entry["third"]["third"]:
                if entry["third"]["third"][0]["stream"]:
                    
                    for item in entry["third"]["third"][0]["values"]:
                        timestamp_ns = int(item[0])
                        timestamp_s = timestamp_ns / 1e9

                        closest_timestamp = min(loadCons[counter], key=lambda t: abs(t - timestamp_s))

                        ## new timestamp to value 
                        mapped_values[item[1]] = closest_timestamp


                    requests_per_timestamp = defaultdict(int)  
                    for timestamp in mapped_values.values():
                        requests_per_timestamp[timestamp] += 1
                    
                    sorted_requests = sorted([(ts, count) for ts, count in requests_per_timestamp.items()])
                    loadLogs.append(sorted_requests)
                    isRawLogs = True
            counter += 1
  
    else:
        ## cons
        for entry in data["results"]["first"]["first"]:

            loadCons_dict = {item[0]: item[1] for item in entry[0]["values"] }
            loadCons.append(loadCons_dict)

         
            
        counter = 0
        ## logs
        for entry in data["results"]["first"]["second"]:
            mapped_values = {}
            if entry[0]["stream"] != None:

                for item in entry[0]["values"]:
                        timestamp_ns = int(item[0])
                        timestamp_s = timestamp_ns / 1e9

                        closest_timestamp = min(loadCons[counter], key=lambda t: abs(t - timestamp_s))

                        mapped_values[item[1]] = closest_timestamp
          
                        
                requests_per_timestamp = defaultdict(int)  
                for timestamp in mapped_values.values():
                    requests_per_timestamp[timestamp] += 1


                sorted_requests = sorted([(ts, count) for ts, count in requests_per_timestamp.items()])

                loadLogs.append(sorted_requests)
 
                isRawLogs = True


            counter += 1

                
    resultEfficiency = calcLogBasedEfficiency(loadCons, loadLogs, type_param, metadata)


    save_data_to_json('type13_results.json', {
        'Efficiency_result': resultEfficiency
    }, gload)

    save_data_to_json('all_results_data.json', all_results_data, gload)
    return check_result(resultEfficiency, operator, threshold)












@app.post("/type14/{type_param}",response_model=bool)
async def check_slo(type_param: int, request: Request):
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

    # if len(data["results"]["first"]) > 2:
    if isinstance(data["results"]["first"], list):

        counter = 0
        # iterate over repetitions
        for entry in data["results"]["first"]:

            ## load stage cons
            loadCons_dict = {item[0]: item[1] for item in entry["third"]["second"][0]["values"] }

            loadCons.append(loadCons_dict)








            ## load stage logs
            if entry["third"]["third"]:
                # mapped_values = {}
                mapped_values = defaultdict(float)
                
                for item in entry["third"]["third"][0]["values"]:
                        timestamp_s = int(item[0])
                        
                        closest_timestamp = min(loadCons[counter], key=lambda t: abs(t - timestamp_s))
                        # mapped_values[item[1]] = loadCons_dict[closest_timestamp]
                        mapped_values[closest_timestamp] += float(item[1])

                mapped_list = list(mapped_values.items())
                loadLogs.append(mapped_list)
                counter += 1
                # loadLogs.append(entry["third"]["third"][0]["values"])
                
    else:
        ## cons
        for entry in data["results"]["first"]["first"]:

            
            loadCons_dict = {item[0]: item[1] for item in entry[0]["values"] }


            loadCons.append(loadCons_dict)
            


        counter = 0
        ## logs
        for entry in data["results"]["first"]["second"]:
            
            mapped_values = defaultdict(float)


            ## item -> timestamp float [0] + value str/float [1]
            for item in entry[0]["values"]:
                timestamp_s = int(item[0])
                
                closest_timestamp = min(loadCons[counter], key=lambda t: abs(t - timestamp_s))
                
                
                mapped_values[closest_timestamp] += float(item[1])


            


            
            
            mapped_list = list(mapped_values.items())
            
            loadLogs.append(mapped_list)
            counter += 1
 

    result = calcLogBasedEfficiency(loadCons, loadLogs, type_param, metadata)

    logger.info("Efficiency result: %d", result)
    save_data_to_json('type14_results.json', {
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