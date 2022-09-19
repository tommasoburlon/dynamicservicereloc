import requests
import os
import json
import sys

local_ip = os.environ["IP_ADDR"]
local_mac = os.environ["MAC_ADDR"]
controller_ip = os.environ["CONTROLLER_IP"]

base_url = "http://" + controller_ip + ":8080/dynamicreloc/"


def status():
    url = base_url + "status"
    headers = {"Content-Type": "application/json"}

    try:
        response = requests.get(url, headers=headers).json()
    except:
        response = {"status": 0, "reason": "host unreachable"}
    return response

def sub(service):
    url = base_url + "server/subscribe"
    headers = {'Content-Type': 'application/json'}
    payload = {'ip': local_ip, 'mac': local_mac, 'service': service, 'priority': 0}

    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers).json()
    except:
        response = {"status": 0, "reason": "host unreachable"}
    return response

def unsub(service):
    url = base_url + "server/unsubscribe"
    headers = {'Content-Type': 'application/json'}
    payload = {'ip': local_ip, 'mac': local_mac, "service": service}

    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers).json()
    except:
        response = {"status": 0, "reason": "host unreachable"}
    return response

def help():
    return '''Server commands:
    -sub <service-name>: link server to  service: <service-name>
    -unsub <service-name>: unlink server to service: <service-name>
    -help: print this guide'''

if  __name__== "__main__":
    if len(sys.argv) == 3:
        if sys.argv[1] == "sub":
            print(json.dumps(sub(sys.argv[2])))
        if sys.argv[1] == "unsub":
            print(json.dumps(unsub(sys.argv[2])))
    if len(sys.argv) == 2:
        if sys.argv[1] == "help":
            print(help())
        if sys.argv[1] == 'status':
            print(json.dumps(status()))
