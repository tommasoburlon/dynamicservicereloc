import json
import traceback

class Reasons: # error enum
    HOST_UNREACHABLE = "host unreachable"
    SERVICE_UNREACHABLE = "service not found"
    SERVER_REUSED = "ip already used for another service"

def exec(host, cmd): 
    """
    utility function to execute a command and parse the result
    """
    print("execute: " + cmd + ", on host: " + host.name, end = " ", flush = True)
    ret = host.cmd(cmd)
    return json.loads(ret)

class Client:
    """
    Wrapper class for a client, useful to call directly the command of a client
    """
    def __init__(self, host):
        self.services = {} #here are stored the virtual ip for every service
        self.host = host
    def sub(self, service):
        ret = exec(self.host, "sub " + service)
        if ret['status'] == 1:
            self.services[service] = ret['ip']
        return ret
    def unsub(self, service):
        ret = exec(self.host, "unsub " + service)
        if service in self.services:
            del self.services[service]
        return ret
    def get(self, service = None, ip = None):
        ret = {'status': '0', 'reason': 'no entry for service'}
        if service != None and service in self.services:
           ret = exec(self.host, "get " + self.services[service])
        elif ip != None:
           ret = exec(self.host, "get " + ip)
        return ret
           
    def status(self):
        return exec(self.host, "status")
    def getIP(self):
        return self.host.IP()

class Server:
    """
    Wrapper class for a server, useful to call directly the command of a function
    """
    def __init__(self, host):
        self.host = host
    def sub(self, service):
        return exec(self.host, "sub " + service)
    def unsub(self, service):
        return exec(self.host, "unsub " + service)
    def status(self):
        return exec(self.host, "status")
    def getIP(self):
        return self.host.IP()


def response_assert(response, status, reason = None, ip = None):
     print("response: " + str(response), end = "... ")
     assert(response['status'] == status)
     if response['status'] == 0:
         assert(response['reason'] == reason)
     if ip != None:
         assert(response['response'] == ip)
     print("...OK")
     return response

def test1(clients, servers):
    """
    testing the standard execution flow: server subscription -> client subscription -> client service -> server unsubscription
    """
    response_assert( servers[0].sub("service1"),  1 )
    response_assert( clients[0].sub("service1"),  1 )
    response_assert( clients[0].get("service1"),  1, ip = servers[0].getIP() )
    response_assert( servers[0].unsub("service1"), 1)

def test2(clients, servers):
    """
    testing client subscription: if the service is unavailable an error is return
    if a client tries to connect to a service before is subscribed an error is return
    """
    response_assert( clients[0].sub("service1"),  0, reason = Reasons.SERVICE_UNREACHABLE )
    response_assert( servers[0].sub("service1"),  1 )
    status = response_assert( servers[0].status(),  1 )
    service_ip = status['services']['service1']['virtual_ip']
    response_assert( clients[0].get(ip = service_ip),  0, reason = Reasons.HOST_UNREACHABLE )
    response_assert( clients[0].sub("service1"),  1 )
    response_assert( clients[0].get(ip = service_ip),  1)
    response_assert( servers[0].unsub("service1"), 1)

def test3(clients, servers):
    """
    testing server relocation: the last server subscribed should handle the service
    """
    response_assert( servers[0].sub("service1"),  1 )
    response_assert( clients[0].sub("service1"),  1 )
    response_assert( clients[0].get("service1"),  1, ip = servers[0].getIP())
    response_assert( servers[1].sub("service1"),  1 )
    response_assert( clients[0].get("service1"),  1, ip = servers[1].getIP())
    response_assert( servers[1].unsub("service1"),  1 )

def test4(clients, servers):
    """
    testing server uniqueness: a single ip should handle at most one service,
    testing multiple client connection to a single service
    """
    response_assert( servers[0].sub("service1"),  1 )
    response_assert( servers[0].sub("service2"),  0, reason = Reasons.SERVER_REUSED )
    response_assert( clients[0].sub("service1"),  1 )
    response_assert( clients[1].sub("service1"),  1 )
    response_assert( clients[0].get("service1"),  1, ip = servers[0].getIP())
    response_assert( clients[1].get("service1"),  1, ip = servers[0].getIP())
    response_assert( clients[0].unsub("service1"),  1 )
    status = response_assert( servers[0].status(),  1 )
    service_ip = status['services']['service1']['virtual_ip']
    response_assert( clients[0].get(ip = service_ip),  0, reason = Reasons.HOST_UNREACHABLE)
    response_assert( servers[0].unsub("service1"),  1 )

def test5(clients, servers):
    """
    testing multiple service connection to a single client
    """
    response_assert( servers[0].sub("service1"),  1 )
    response_assert( servers[1].sub("service2"),  1 )
    response_assert( clients[0].sub("service1"),  1 )
    response_assert( clients[0].sub("service2"),  1 )
    response_assert( clients[0].get("service1"),  1, ip = servers[0].getIP())
    response_assert( clients[0].get("service2"),  1, ip = servers[1].getIP())
    response_assert( servers[0].unsub("service1"),  1 )
    response_assert( servers[1].unsub("service1"),  1 )

def executeTest(name, desc, testFunc, clients, servers):
    print("---------- test " + name + " ----------")
    print(desc)
    response = "PASSED"
    try:
        testFunc(clients, servers)
        print("TEST PASSED")
    except Exception as e:
        traceback.print_exc()
        print("TEST FAILED")
        response = "FAILED"
    print("---------------------------" + len(name) * "-")
    return response

def testNetwork(clients_node, servers_node):
    print("----- LAUNCH TESTING -----")
    
    #create the object wrapper
    clients = []
    servers = []

    for c in clients_node:
        clients.append(Client(c))
    for s in servers_node:
        servers.append(Server(s))

    #executes the tests
    results = []
    results.append( executeTest("1", "testing client subscription, client request",                        test1, clients, servers) )
    results.append( executeTest("2", "testing message of host unreachable",                                test2, clients, servers) )
    results.append( executeTest("3", "testing service relocation",                                         test3, clients, servers) )
    results.append( executeTest("4", "testing server uniqueness, client unsubscription, multiple clients", test4, clients, servers) )
    results.append( executeTest("5", "testing client with multiple subscription",                          test5, clients, servers) )

    #print results
    print("resume: ")
    ctr = 0
    passed = 0
    for r in results:
        ctr += 1
        print("test n." + str(ctr) + ": " + r)
        if r == "PASSED":
            passed += 1
    print("test passed: " + str(passed) + "/" + str(ctr))
      

