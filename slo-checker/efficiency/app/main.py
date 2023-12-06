from fastapi import FastAPI,Request
import logging
import os
import json
import sys
import re
import pandas as pd
import csv


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


        














@app.post("/",response_model=bool)
async def check_slo(request: Request):
    data = json.loads(await request.body())
    logger.info('Received request with metadata: %s', data['metadata'])
    # Open a file for writing
    with open('output.csv', 'w', newline='') as file:
        # Create a CSV writer
        writer = csv.writer(file)

        # Write the header (keys of the first dictionary in the list)
        writer.writerow(data[0].keys())

        # Write the data rows
        for item in data:
            writer.writerow(item.values())


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