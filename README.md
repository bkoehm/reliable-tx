Reliable Spring Transactions
============================

## License and Copyright

[Apache License, v2.0](LICENSE) and [Copyright Notice](NOTICE).

## Spring

Instantiate a
[ManagedSpringTransactionImpl](reliable-tx-spring/src/main/java/software/reliabletx/spring/ManagedSpringTransactionImpl.java)
to get a managed transaction.  This implementation will start a new
transaction and will enforce that when you call `commit()` or `rollback()`,
the managed transaction is still the current active transaction in the
underlying Spring transaction manager.  It also gives you the ability to
call the `getSynchronizationState()` method on the managed transaction
object to get the current state of the transaction.

## Camel

This is best explained (although somewhat abstractly) using an example.

Say you're setting up a JMS Consumer using Camel.  (Note: It doesn't have to
be JMS.  The `from()` component can be any Camel component.)

One way in Camel to configure a JMS consumer (without using reliable-tx):
```
from(jmsQueueUri)
    .to(destinationUri)
```

If you want to add reliable-tx semantics to this consumer, you would now
configure the consumer this way:
```
consumerBuilder
    .from(jmsQueueUri, ErrorResponseMode.EXCEPTION_AS_REPLY, this)
    .to(destinationUri);
```

Where `consumerBuilder` is an instantiated
[ReliableTxConsumerBuilder](reliable-tx-camel/src/main/java/software/reliabletx/camel/ReliableTxConsumerBuilder.java)
object and `this` is the instance of the
`org.apache.camel.builder.RouteBuilder` object that the route is being
configured in.

For a more complete working example, see the tests in
[JtaTransactionTest](reliable-tx-camel/src/test/java/software/reliabletx/camel/JtaTransactionTest.java).

### ErrorResponseMode

There are two options: `EXCEPTION_AS_REPLY` and `EXCHANGE_FAILURE_NO_REPLY`. 
These are also documented in
[ErrorResponseMode](reliable-tx-camel/src/main/java/software/reliabletx/camel/ErrorResponseMode.java),
but:
* `EXCEPTION_AS_REPLY`
  * Use this when using the `InOut` exchange pattern and you want consumer
    exceptions, if they occur, to be sent as the reply.  In this mode, the
    Camel Exchange is not marked as failed since a reply is sent.  In the
    context of messaging, such as JMS, there is no opportunity for the
    message broker to put the failed message on a DLQ (Dead Letter Queue)
    because Camel commits the original exchange transaction in order to send
    the reply.
* `EXCHANGE_FAILURE_NO_REPLY`
  * Use this when using any kind of exchange pattern, including `InOnly` and
    you want the Camel exchange to fail.  In this mode, consumer exceptions
    are not put into the reply.  Instead, the Camel exchange is marked as
    failed and the exchange.getException() method will return the exception. 
    Because Camel rolls back the original exchange transaction, no reply
    will be sent.  In the context of messaging, such as JMS, the JMS
    producer client that sent the message to the queue will never see a
    reply (assuming you're using `InOut` pattern).  You'll either want the
    client to be configured with a wait-for-reply-timeout value so it times
    out within a reasonable time limit or you'll want the client to use the
    `InOnly` pattern so it never waits for a reply.  Additionally, since the
    JMS transaction is rolled back on the broker side, the broker, if
    configured to do so, has the opportunity to move the failed message to a
    DLQ.  DLQ-handling behavior is entirely dependent on the broker
    configuration.

### Chaining route contexts

If you build a route that contain multiple contexts, then as a message flows
through the route during the exchange, a new transaction will be created for
each route context per the exchange.

For example:
```
consumerBuilder
    .from(jmsQueueUri, ErrorResponseMode.EXCHANGE_FAILURE_NO_REPLY, this)
    .to(middleMan);
consumerBuilder
    .from(middleMan, ErrorResponseMode.EXCHANGE_FAILURE_NO_REPLY, this)
    .to(terminatingEndpoint);
```

* Managed Transaction #1: as the message routes between `jmsQueueUri` and
  the `middleMan`.
* Managed Transaction #2: as the message routes between `middleMan` and the
  `terminatingEndpoint`.

Note the second route, `middleMan` to `terminatingEndpoint`, doesn't
necessarily have to be built with the `consumerBuilder` for these semantics
to hold true.  The same is true if only the first route was built with
`consumerBuilder`.

This may differ from normal Camel operation, where without
`consumerBuilder`, the exchange could be one transaction.

### Using EnhancedDefaultJmsMessageListenerContainer

If you're consuming transactionally off a JMS source point (e.g., a queue or
topic), you probably want to use
`software.reliabletx.camel.jms.EnhancedDefaultJmsMessageListenerContainer`
so a reliabletx managed transaction is started right at the beginning.

To get Camel to use this enhanced message listener, create a bean:
```
<bean id="jmsListenerContainerFactory"
        class="software.reliabletx.camel.jms.EnhancedDefaultMessageListenerContainerFactory" />
```

Then however you create your JmsEndpoint, adjust the endpoint configuration
like so:
```
myJmsEndpoint.getConfiguration().setMessageListenerContainerFactory(jmsListenerContainerFactory);
myJmsEndpoint.getConfiguration().setConsumerType(ConsumerType.Custom);

```
or you can use Spring XML syntax to do it if you're creating your endpoint
as a bean.
