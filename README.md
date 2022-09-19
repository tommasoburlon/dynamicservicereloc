# dynamicservicereloc
Simple SDN implementation using floodlight of a dynamuic service relocation module.

##profect architecture
the floodlight directory conatins all the files and directories needed by the floodlight
application to build the module and execut it.
The example directory contains two simple server/client application, a simple mininet
topology file, a simple automate testing application written in python.

##building the floodlight module

execute:

		sh $(REPODIR)/build.sh $(REPODIR) $(FLOODLIGHT)

where $(REPODIR) is the directory of this repository on your local machine, and $(FLOODLIGHT)
is the root directory of the floodlight application

##executing the example applications and the tests

To execute the example applications on mininet run:

		sudo python3 network.py <IP-ADDRESS>
		
inside the example directory, where <IP-ADDRESS> is the ip address of the machine running the controller
it is needed to set the interface between the virtual devices of mininet and the REST interface of the
floodlight module.

To execute the automate tests run:

		sudo python3 network.py <IP-ADDRESS> test
