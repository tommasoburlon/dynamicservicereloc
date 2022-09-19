from mininet.topo import Topo

class ExampleTopo(Topo):
    "Example topology"

    def __init__(self):
        self.client = []
        self.server = []
        self.switch = []
        self.controller = None
        Topo.__init__(self)

    " utility methods to add Switches, Servers, and Clients"
    def createNClient(self, base_name, n):
        client = []
        for i in range(n):
            h = self.addHost(base_name + str(i))
            self.client.append(h)
            client.append(h)
        return client
  
    def createNSwitch(self, base_name, n):
        switch = []
        for i in range(n):
            s = self.addSwitch(base_name + str(i))
            self.switch.append(s)
            switch.append(s)
        return switch
  
    def createNServer(self, base_name, n):
        server = []
        for i in range(n):
            h = self.addHost(base_name + str(i))
            self.server.append(h)
            server.append(h)
        return server

    "builder of the network"
    def build(self):

        server = self.createNServer("server", 6)
        switch = self.createNSwitch("switch", 4)
        client = self.createNClient("client", 2)

        self.addLink(switch[0], client[0])
        self.addLink(switch[0], client[1])

        self.addLink(switch[0], switch[1])
        self.addLink(switch[0], switch[2])
        self.addLink(switch[0], switch[3])

        self.addLink(switch[1], server[0])
        self.addLink(switch[1], server[1])
        self.addLink(switch[2], server[2])
        self.addLink(switch[2], server[3])
        self.addLink(switch[3], server[4])
        self.addLink(switch[3], server[5])

        self.controller = self.addHost("controller")
        self.addLink(self.controller, switch[0])

topos = {'mytopo': (lambda: ExampleTopo())}
