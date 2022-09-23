from mininet.net import Mininet
from mininet.node import OVSController, RemoteController
from mininet.log import lg, info
from mininet.cli import CLI
from mininet.link import Intf
import re
from mininet.util import quietRun
from topology import ExampleTopo
from testing import testNetwork
import sys
import socket
import traceback

if len(sys.argv) < 2:
    print("please specify ip adress of the machine running the controller")
    exit()
controllerIP = sys.argv[1] #ip of the machine where mininet run

topo = ExampleTopo() #create topology

network = Mininet(topo=topo, controller=RemoteController, waitConnected=True) #build network

network.addNAT().configDefault() #add NAT for the REST interface on the controller

network.start()

clients = []
servers = []

for host in network.hosts:
    #setting the environment variable with all the config information
    host.cmd("export IP_ADDR=" + host.IP())
    host.cmd("export MAC_ADDR=" + host.MAC())
    host.cmd("export CONTROLLER_IP=" + controllerIP)

    #aliasing the command for simpler interface, instead of calling python explictily
    if "client" in host.name:
        clients.append(host)
        commands = ["sub", "unsub", "help", "get", "status"]
        for command in commands:
            host.cmd('alias {0}="python3 client.py {0}"'.format(command))
        print("starting client:", host.name + ",", "ip:", host.IP())

    if "server" in host.name:
        servers.append(host)
        commands = ["sub", "unsub", "help", "status"]
        for command in commands:
            host.cmd('alias {0}="python3 server_command.py {0}"'.format(command))
        print("starting server:", host.name + ",", "ip:", host.IP())
        host.cmd("python3 server.py &> /dev/null &")

if len(sys.argv) < 2 or sys.argv[2] == "demo":
    CLI( network )
elif sys.argv[2] == "test":
    try:
    	testNetwork(clients, servers)
    except Exception as e:
        traceback.print_exc()
else:
    print("unrecoginized argument (should be demo or test)")


network.stop()

