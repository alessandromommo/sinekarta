# How to create a development environment #

## Download and compile sources ##

  * download and install 4.2.f version of alfresco.
    * from now these instructions refers to an Alfresco installation done on Ubuntu Linux using sudo
    * Alfresco installation dir is  /opt/alfresco-4.2.f.skds
  * download sources from svn repository https://sinekarta.googlecode.com/svn/trunk/
  * we use eclipse as development environment. We also use jboss tools for maven integration
  * import following maven projects :
    * sinekarta-ds/itext-jdk16 (patch to itext for bouncycastle jdk16 support)
    * sinekarta-ds/xades4j-jdk16 (patch to xades4j for bouncycastle jdk16 support)
    * sinekarta-ds/sinekarta-ds-applet (sign applet)
    * sinekarta-ds (super project)
      * sinekarta-ds/sinekarta-ds-alfresco (Alfresco core plugin)
      * sinekarta-ds/sinekarta-ds-commons (commons tools and utilities)
      * sinekarta-ds/sinekarta-ds-core (core signature algorithms)
      * sinekarta-ds/sinekarta-ds-share (alfresco share plugin)
      * sinekarta-ds/sinekarta-ds-integration (integration test)
  * select all projects, right click and run maven update (force update of snapshot if necessary)

> ### Now you should have a running eclipse workspace (no compilation problems) ###

These are projects :
  * sinekarta-ds (generic project)
  * itext-jdk16 (java project)
  * xades4j-jdk16 (java project)
  * sinekarta-ds-alfresco (java project)
  * sinekarta-ds-commons (java project)
  * sinekarta-ds-core (java project)
  * sinekarta-ds-share (java project)
  * sinekarta-ds-applet (java project)
  * sinekarta-ds-integration (java project)

## Running the build ##

  * install itext-jdk16 on local repository :
    * right click on /itext-jdk16/pom.xml
    * Run As -> Maven install
    * wait for "BUILD SUCCESS" in eclipse console
  * install xades4j-jdk16 on local repository :
    * right click on /xades4j-jdk16/pom.xml
    * Run As -> Maven install
    * wait for "BUILD SUCCESS" in eclipse console
  * install sinekarta-ds-applet on local repository :
    * right click on /sinekarta-ds-applet/pom.xml
    * Run As -> Maven install
    * wait for "BUILD SUCCESS" in eclipse console
  * create a directory named work in your workspace directory
  * copy alfresco.war and share.war (you can find these on /opt/alfresco-4.2.f.skds/tomcat/webapps) into work directory created
    * could be that you have to use sudo to make the copy
    * could be that you have to change permissions and/or ownership
  * rename alfresco.war in .alfresco-original.war (hidden file)
  * rename share.war in .share-original.war (hidden file)
  * update sinekarta-ds
    * select sinekarta-ds project
    * right click /sinekarta-ds
    * Maven -> Update Project ...
    * select all
    * press ok and wait for  maven update finish
  * put your JENIA\_Software.p12 (official certificate for applet sign) in your workspace directory
  * resolve dependencies for ant tasks :
    * right click /sinekarta-ds/pom.xml
    * Run As -> Maven build...
    * set "package" in "Goals" field
    * press "Run" button
    * wait for "BUILD SUCCESS" in eclipse console

## Result of build ##

You can find these artifact as result of build process :
  * sinekarta-ds/sinekarta-ds-alfresco/target/dist/sinekarta-ds-alfresco.amp - amp to be installed into alfresco
  * sinekarta-ds/sinekarta-ds-share/target/dist/sinekarta-ds-share.amp - amp to be installed into share

To install amps into alfresco and share war, please refer to alfresco-mmt tool documentation.

You also have a full version of alfresco.war and share.war "amped" into work directory.

## Launch Alfresco ##

Now you have an alfresco.war and share.war into work directory.
Move/copy these war ino /opt/alfresco-4.2.f.skds/tomcat/webapps replacing originals.
Now you can run Alfresco.

## If you need to modify the applet... ##

If you have to modify the applet sources, please does not forgot to call maven install on sinekarta-ds-applet on your local maven repository before to run skds tests.