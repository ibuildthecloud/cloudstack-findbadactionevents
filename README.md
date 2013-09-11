cloudstack-findbadactionevents
==============================

Run with

mvn exec:java -Dexec.mainClass=asm.FindCallers -Dexec.arguments='/somepath/src/cloudstack/,Lcom/cloud/event/ActionEvent;,output.csv'

Replace /somepath/src/cloudstack with the root of your cloudstack folder.

This will output a file in the current directory called "output.csv" which is a tab seperated CSV in the format

whether call is ok, source class, source method name, target class name, target method name, method descriptor
