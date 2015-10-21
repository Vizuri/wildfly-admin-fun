# WildFly Admin Examples

WildFly is an application server that was formerly known as JBoss AS.
The following are examples of deploying applications to WildFly using several means and includes some configuration tasks.

The different task groups are sectioned off in **git** branches.

## Setup

These examples assume that you have a local Wildfly 8.2.1.Final instance installed.  If you have a JDK installed, this can be as easy as:

* Unzip the server from [WildFly 8.2.1.Final Download](http://download.jboss.org/wildfly/8.2.1.Final/wildfly-8.2.1.Final.zip)
* Examples assume an environment variable $JBOSS_HOME that points to that directory exists
  * e.g. export JBOSS_HOME=/Users/home/wildfly-8.2.1.Final
* Create a managment user (to be used in the Management Web UI), do not specify any groups for the user, default to the ManagementRealm and answer `no` on connecting to another AS process
  * $JBOSS_HOME/bin/add-user.sh
  * Examples will use 1 window to run the server and 1 to run code examples


## Step 0: Simple Deployments/Undeployments

Quick demonstration of interacting with a standalone server, using the maven plugin.

### Deploy kitchensink to standalone with maven script

Start 2 terminal window sessions and start the server in one of them:  
	
	$JBOSS_HOME/standalone.sh
	
Checkout code in the other shell terminal, build and deploy: 

	git clone https://github.com/Vizuri/wildfly-admin-fun
	
	cd wildfly-admin-fun
	
	git checkout step0-branch
	
	mvn -pl kitchensink clean package wildfly:deploy
	
Using your browser, go to the following URLs to your server using different tabs:

Demo App: [http://localhost:8080/wildfly-kitchensink](http://localhost:8080/wildfly-kitchensink)

Management Console: [http://localhost:9990](http://localhost:9990) (login with management user created in setup)

Undeploy the application using Maven plugin:

	mvn -pl kitchensink wildfly:undeploy
	
Read more about the [WildFly Maven plugin](https://docs.jboss.org/wildfly/plugins/maven/latest/)

### Deploy to domain with management UI
* Stop running standalone server (ctrl-c on window)
* Start domain: $JBOSS_HOME/domain.sh
* Use UI to deploy  [http://localhost:9990](http://localhost:9990 "Wildfly admin console")
	* Deployments
	* Add > Choose file kitchensink/target/wildfly-kitchensink.war
	* Assign > Choose “main-server-group”
		* Make sure “enable” is checked, or can do as a separate step
		* This deploys to both servers running on the main server group:
		* [http://localhost:8080/wildfly-kitchensink](http://localhost:8080/wildfly-kitchensink "Server A")
		* [http://localhost:8230/wildfly-kitchensink](http://localhost:8230/wildfly-kitchensink "Server B")
* Cleanup: Deployments > Remove wildfly-kitchensink.war

## Step 1: Deploy with CLI
Some setup: updating to use 2 server groups, using same profile and a port offset of 100 for the second server:

* **Shut Down** the server running from previous step if running (ctrl-c)
	* Direct changes to configuration files will be lost when server shuts down
* Update $JBOSS_HOME/domain/configuration/domain.xml
	* Change profile of the “other-server-group” to be “full” instead of “full-ha”
	* Change the socket-binding-group to be “full-sockets” instead of “full-ha-sockets” for the “other-server-group”
* Update $JBOSS_HOME/domain/configuration/host.xml
	* Set “server-two” to have “false” for auto-start
	* Set “server-three” to have “true” for auto-start
	* Set port-offset for server-three to be 100

In the previous step, we deployed the application to 2 different servers in one action because they are both in the same server group.  We are starting to setup a rolling deployment capability, so we need 2 distinct server groups running.  We need port offsets when we run multiple Wildfly server instances on the same machine.  

We also need different names for the application artifact to differentiate the different versions, so we have modified the final name of the war to include the version (i.e. wildfly-kitchensink-9.0.0-SNAPSHOT.war).

Build step1

	git checkout step1-branch
	mvn -pl kitchensink clean package

	# Deploy with script
	$JBOSS_HOME/bin/jboss-cli.sh --file=deploy.cli
	
**Verify**	
[http://localhost:8080/wildfly-kitchensink](http://localhost:8080/wildfly-kitchensink "Server A")
[http://localhost:8180/wildfly-kitchensink](http://localhost:8180/wildfly-kitchensink "Server B")

**Problem?**
_Using different in-memory databases…_  Changes in one server are not reflected in the other...  we'll fix that in the next step.

**Cleanup…** Deployments > remove

## Step 2: Configure Database through CLI

To perform this step you will need access to a local MySQL database server.

### Setup…
Create a local mysql database ‘wildfly’:

	create database wildfly;
	create user 'wildfly'@'localhost' identified by 'wildfly';
	grant all privileges on wildfly.* to 'wildfly'@'localhost';
	
### Build next version

	git checkout step2-branch
	mvn -pl kitchensink clean package
	
**Changelog:**
* Bumped app version to 9.0.1
* Removed embedded datasource
* Changed persistence.xml to 'update' rather than 'create-drop'
* Added mysql.cli to do the database setup
* Modified deploy.cli for new version

### Setup database

	$JBOSS_HOME/bin/jboss-cli.sh --file=mysql.cli
	
This does the following

* Creates a new module library for the msyql jar
	* A little sleight of hand… only works through CLI for local domain if not connected
* Connects
* Creates the jdbc driver for the mysql database
* Creates a datasource that we can get from JNDI

### Verify database connection:
**CLI kung fu:**

	/host=master/server=server-one/subsystem=datasources/data-source=QuickStartDS:test-connection-in-pool
	
**UI:**  
Runtime > Subsystems > Datasources > Test Connection

### Deploy (like before)

	$JBOSS_HOME/bin/jboss-cli.sh --file=deploy.cli
	
### Verify setup (changes in one should be reflected in the other)

[http://localhost:8080/wildfly-kitchensink](http://localhost:8080/wildfly-kitchensink "Server A")
[http://localhost:8180/wildfly-kitchensink](http://localhost:8180/wildfly-kitchensink "Server B")

## Step3: Rolling deploy? Groovy!
Deploy with groovy script (step-wise rolling deploy)

### Build new version

	mvn -pl kitchensink clean package
	
**Change log**

* Version bump to 9.0.2
* Added groovy script

### Run through “rolling deployment”

	groovy -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar deploy.groovy
	
**Hit enter to advance**

* _Step 0:_ displays running state of servers
* _Step 1:_ “stages” deployment of new version 9.0.2
	* Verify: look in management console in deployments
	* Has both 9.0.1 (with 2 assignments), and 9.0.2 (with 0)
* _Step 2:_ Remove old version from main server group
	* 9.0.1 (1 assignment)
	* 9.0.2 (0 assignments)
* _Step 3:_ Deploys new version to first group
	* 9.0.1 (1 assignment)
	* 9.0.2 (1 assignment)
	* [http://localhost:8080/wildfly-kitchensink](http://localhost:8080/wildfly-kitchensink "Server A") (Notice version 2 banner!)
	* [http://localhost:8180/wildfly-kitchensink](http://localhost:8180/wildfly-kitchensink "Server B") (Still version 1)
* _Step 4:_ Updates other server group

Can interject load balancer instructions to steps 2, 3 and 4 to get a seamless rolling deployment.



