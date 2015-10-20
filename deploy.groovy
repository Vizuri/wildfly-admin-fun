import org.jboss.as.cli.scriptsupport.* 
       
Void printServerState (CLI cli, String serverName) {
	result = cli.cmd("/host=master/server=" + serverName + ":read-attribute(name=server-state) ") 
	response = result.getResponse() 
	serverstate = response.get("result")
	        
	println("Server '" + serverName +"' state: " + serverstate) 
}

Void removeApp(CLI cli, String serverGroup, String version) {
	result = cli.cmd("/server-group=" + serverGroup + "/deployment=wildfly-kitchensink-" + version + ".war:remove()")
	response = result.getResponse() 
	result = response.get("result")
	        
	println("Version " + version + " removal from group " + serverGroup + " result: " + result) 
}

Void addApp(CLI cli, String serverGroup, String version) {
	result = cli.cmd("deploy --name=wildfly-kitchensink-" + version + ".war --server-groups=" + serverGroup)
	response = result.getResponse() 
	result = response.get("result")
	        
	println("Version " + version + " added to group " + serverGroup + " result: " + result) 
}

cli = CLI.newInstance() 
cli.connect("localhost", 9990, "admin", "admin99!".toCharArray()) 
              
println("Step 0: Initial state")
printServerState(cli, "server-one")
printServerState(cli, "server-three") 
      
newVersion = "9.0.2"
oldVersion = "9.0.1"
console = System .console()

println("Step 1: Stage deployment for version " + newVersion + "... press key for step 2")
response = cli.cmd("deploy kitchensink/target/wildfly-kitchensink-" + newVersion + ".war --runtime-name=wildfly-kitchensink.war --disabled")
console.readLine('>')

println("Step 2: Undeploy old from first group")
removeApp(cli, "main-server-group", oldVersion)
console.readLine('>')

println("Step 3: Deploy new to first group")
addApp(cli, "main-server-group", newVersion)
console.readLine('>')

println("Step 4: Update second group")
removeApp(cli, "other-server-group", oldVersion)
addApp(cli, "other-server-group", newVersion)

cli.disconnect()