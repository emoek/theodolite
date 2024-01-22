from fastapi import FastAPI,Request
import logging
import os
import json
import sys
import re
import pandas as pd


app = FastAPI()

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
    elif re.search(r'^p\d\d?(\.\d+)?$', func_string): # matches strings like 'p99', 'p99.99', 'p1', 'p0.001'
        def percentile(x):
            return x.quantile(float(func_string[1:]) / 100)
        percentile.__name__ = func_string
        return percentile
    else:
        raise ValueError('Invalid function string.')

def aggr_query(values: dict, warmup: int, aggr_func):
    # logger.info("SECOND:",values)
    # logger.info("TYPE:",type(values))
    # logger.info(len(values))
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
    



def handle_query(data):
    print("call if workloadQuery provided, thus ")


def handle_efficiency(data):
    load = data[1]
    results = data[0]

    for r in results:
        # potentially base 
        first_pair = r['first'] 
        first_string = str(first_pair['first'])
        first_first_listOfProms = first_pair['second']
        first_second_listOfProms = first_pair['third']


        second_pair = r['second'] 
        second_string = str(second_pair['first'])
        second_first_listOfProms = second_pair['second']
        second_second_listOfProms = second_pair['third']


        # if(len(first_first_listOfProms) < 1):
        #     query_results = [aggr_query(r[0]["values"], warmup, query_aggregation) for r in data["results"]]


        




def calcStagedConsumption(baseConsData, loadConsData, metadata):
    
    warmup = int(metadata['warmup'])
    query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    # operator = metadata['operator']
    # threshold = float(metadata['threshold'])

    # baseConsQueryResults = [aggr_query(r[0]["values"], warmup, query_aggregation) for r in baseConsData]
    # loadConsQueryResults = [aggr_query(r[0]["values"], warmup, query_aggregation) for r in loadConsData]

    # logger.info("FIRST:",baseConsData)
    # logger.info("TYPE:",type(baseConsData))
    # logger.info("LEN:",len(baseConsData))


    
    # print(baseConsData)
    baseConsQueryResults = [aggr_query(r, warmup, query_aggregation) for r in baseConsData]
    loadConsQueryResults = [aggr_query(r, warmup, query_aggregation) for r in loadConsData]
    print("loadresults:",loadConsQueryResults)

    baseResult = pd.DataFrame(baseConsQueryResults).aggregate(rep_aggregation).at[0]
    loadResult = pd.DataFrame(loadConsQueryResults).aggregate(rep_aggregation).at[0]
    print("loadresults:",loadResult)


    result = loadResult - baseResult

    return result


def calcConsumption(loadConsData, metadata):
    
    warmup = int(metadata['warmup'])
    query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    # operator = metadata['operator']
    # threshold = float(metadata['threshold'])

    loadConsQueryResults = [aggr_query(r, warmup, query_aggregation) for r in loadConsData]

    loadResult = pd.DataFrame(loadConsQueryResults).aggregate(rep_aggregation).at[0]

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

    idleResult = pd.DataFrame(idleWMQueryResults).aggregate(rep_aggregation).at[0]
    loadResult = pd.DataFrame(loadWMQueryResults).aggregate(rep_aggregation).at[0]

    result = loadResult - idleResult

    return result


def calcWorkloadMetric(loadWorkloadData, metadata):
    
    warmup = int(metadata['warmup'])
    query_aggregation = get_aggr_func(metadata['queryAggregation'])
    rep_aggregation = get_aggr_func(metadata['repetitionAggregation'])
    # operator = metadata['operator']
    # threshold = float(metadata['threshold'])

    loadWMQueryResults = [aggr_query(r, warmup, query_aggregation) for r in loadWorkloadData]

    loadResult = pd.DataFrame(loadWMQueryResults).aggregate(rep_aggregation).at[0]

    result = loadResult

    return result


def calcStagedWorkloadLog(idleWorkloadData, loadWorkloadData, metadata):
    
  

    nrIdleLogs = [len(r) for r in idleWorkloadData]

    nrLoadLogs = [len(r) for r in loadWorkloadData]


    logIdleResult = pd.DataFrame(nrIdleLogs).aggregate('mean').at[0]
    logLoadResult = pd.DataFrame(nrLoadLogs).aggregate('mean').at[0]



    result = logLoadResult - logIdleResult

    return result


def calcWorkloadLog(loadWorkloadData, metadata):
    
   
    nrLoadLogs = [len(r) for r in loadWorkloadData]
    print(nrLoadLogs)
    logResult = pd.DataFrame(nrLoadLogs).aggregate('mean').at[0]
    print(logResult)


    result = logResult

    return result













@app.post("/type1",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])




    baseCons = []
    loadCons = []
    idleLogs = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])




        if entry["second"]["third"]:
            idleLogs.append(entry["second"]["third"][0]["values"])

        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])




    resultWLQ = calcStagedWorkloadLog(idleLogs, loadLogs, metadata)
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWLQ / resultCons

    return check_result(result, operator, threshold)

    # return True







@app.post("/type2",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])



    loadCons = []
    idleLogs = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        # baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])




        if entry["second"]["third"]:
            idleLogs.append(entry["second"]["third"][0]["values"])

        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])



    resultWLQ = calcStagedWorkloadLog(idleLogs, loadLogs, metadata)
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWLQ / resultCons

    return check_result(result, operator, threshold)

    # return True





@app.post("/type3",response_model=bool)
async def check_slo(request: Request):
    # print("HERE")
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])

    # with open('../resources/efficiency_type3_2rep.json') as f:
    #     data = json.load(f)
        # print(data)

    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])





    # Initialize lists to hold values
    # list of lists with each entry being a list as part of a repetition
    baseCons = []
    loadCons = []
    loadLogs = []

    # counter = 0
    # Extract values
    for entry in data["results"]["first"]:
    # Extract base values
        # baseCons.extend(entry["first"]["second"][0]["values"])
        baseCons.append(entry["first"]["second"][0]["values"])
        
        # Extract idle values
        # idleCons.extend(entry["second"]["second"][0]["values"])
        
        # Extract load values
        # loadCons.extend(entry["third"]["second"][0]["values"])
        loadCons.append(entry["third"]["second"][0]["values"])
        # loadCons[counter] = entry

        # counter = counter + 1


        # Extract log values if present
        # if entry["second"]["third"]:
        #     loadLogs.extend(entry["second"]["third"][0]["values"])


        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])
        else :
            loadLogs.append[1]


    print(loadCons)


    resultWLQ = calcWorkloadLog(loadLogs, metadata)
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWLQ / resultCons

    return check_result(result, operator, threshold)


    # return True







@app.post("/type4",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])



    loadCons = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        

        loadCons.append(entry["third"]["second"][0]["values"])



        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])




    resultWLQ = calcWorkloadLog(loadLogs, metadata)
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWLQ / resultCons

    return check_result(result, operator, threshold)

    # return True







@app.post("/type5",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])


    baseCons = []
    loadCons = []
    idleLogs = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])




        if entry["second"]["third"]:
            idleLogs.append(entry["second"]["third"][0]["values"])

        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])





    resultWMQ = calcStagedWorkloadMetric(idleLogs, loadLogs, metadata)
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWMQ / resultCons

    return check_result(result, operator, threshold)

    # return True








@app.post("/type6",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])




    loadCons = []
    idleLogs = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        

        loadCons.append(entry["third"]["second"][0]["values"])




        if entry["second"]["third"]:
            idleLogs.append(entry["second"]["third"][0]["values"])

        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])



    resultWMQ = calcStagedWorkloadMetric(idleLogs, loadLogs, metadata)
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWMQ / resultCons

    return check_result(result, operator, threshold)

    # return True




@app.post("/type7",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])




    baseCons = []
    loadCons = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])


        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])





    resultWMQ = calcWorkloadMetric(loadLogs, metadata)
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultWMQ / resultCons

    return check_result(result, operator, threshold)

    # return True




@app.post("/type8",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])



    loadCons = []
    loadLogs = []

    
    for entry in data["results"]["first"]:


        

        loadCons.append(entry["third"]["second"][0]["values"])




        # Extract log values if present
        if entry["third"]["third"]:
            loadLogs.append(entry["third"]["third"][0]["values"])



    resultWMQ = calcWorkloadMetric(loadLogs, metadata)
    resultCons = calcConsumption(loadCons, metadata)

    result = resultWMQ / resultCons

    return check_result(result, operator, threshold)

    # return True






@app.post("/type9",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])





    baseCons = []
    loadCons = []


    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])




    values = data["results"]




    resultLoad = values['second']
    print(resultLoad)
    resultCons = calcStagedConsumption(baseCons, loadCons, metadata)

    result = resultLoad / resultCons

    return check_result(result, operator, threshold)

    # return True




@app.post("/type10",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])




    loadCons = []


    
    for entry in data["results"]["first"]:


        

        loadCons.append(entry["third"]["second"][0]["values"])





    values = data["results"]



    resultLoad = values['second']
    resultCons = calcConsumption(loadCons, metadata)

    result = resultLoad / resultCons

    return check_result(result, operator, threshold)

    # return True





@app.post("/type11",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])


    baseCons = []
    loadCons = []


    
    for entry in data["results"]["first"]:


        baseCons.append(entry["first"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])




    resultBaseCons = calcConsumption(baseCons, metadata)
    resultLoadCons = calcConsumption(loadCons, metadata)

    result = resultBaseCons / resultLoadCons

    return check_result(result, operator, threshold)

    # return True







@app.post("/type12",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    operator = metadata['operator']
    threshold = float(metadata['threshold'])




    idleCons = []
    loadCons = []

    
    for entry in data["results"]["first"]:


        idleCons.append(entry["second"]["second"][0]["values"])
        

        loadCons.append(entry["third"]["second"][0]["values"])



    resultIdleCons = calcConsumption(idleCons, metadata)
    resultLoadCons = calcConsumption(loadCons, metadata)

    result = resultIdleCons / resultLoadCons

    return check_result(result, operator, threshold)

    # return True















@app.post("/",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])


    # Open a file for writing
    with open('request_data.json', 'w') as file:
            json.dump(data, file, indent=4)


    metadata = data['metadata']
    warmup = int(data['metadata']['warmup'])
    query_aggregation = get_aggr_func(data['metadata']['queryAggregation'])
    rep_aggregation = get_aggr_func(data['metadata']['repetitionAggregation'])
    operator = data['metadata']['operator']
    threshold = float(data['metadata']['threshold'])




    return True
    # isWithQuery = len(data["results"]) > 1

    # if isWithQuery: 
    #     handle_efficiency(data["results"])
    # else:
    #     handle_query(data["results"])

    # query_results = [aggr_query(r[0]["values"], warmup, query_aggregation) for r in data["results"]]
    # result = pd.DataFrame(query_results).aggregate(rep_aggregation).at[0]
    # return check_result(result, operator, threshold)

logger.info("SLO evaluator is online")