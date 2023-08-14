# camel-rabbitmq

This is a sample orchestrator for Apache Camel which works with ActiveMQ for now and can be used for message consumer and producer operations.

## Getting started

You can login to admin console of ActiveMQ [here](http://172.21.0.65:8161/console/auth/login) with credentials available in application.properties


Here is the sample request which you can send to the "triggerin" queue [here](http://172.21.0.65:8161/console/artemis/artemisAddressSendMessage?nid=root-org.apache.activemq.artemis-broker-addresses-triggerin) as mentioned in the RabbitMQRoute.java which is the starting point of the process flow.

{
	"customerId":123,
	"accountNumber":"1000555",
	"branchId":"123"
}


The complete flow works in following manner:

1. trigger -> log the message and send to accountinfo queue
2. accountinfo -> account service to perform a dummy operation (add account name in this case) -> send enriched message to customerinfo
3. customerinfo -> customer service to perform a dummy operation (add customer name in this case) -> send enriched message to branchinfo
4. branchinfo -> branch service to perform a dummy operation (add branch name in this case) -> send enriched message to transactioninfo
5. transactioninfo (consider this as a last stop in the whole process flow -> orchestrator to log the final message to demonstrate all relevant info has been received.