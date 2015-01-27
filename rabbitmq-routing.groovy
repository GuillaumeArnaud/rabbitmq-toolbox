#!/usr/bin/env groovy

@Grab(group = 'com.rabbitmq', module = 'amqp-client', version = "3.4.3")
import com.rabbitmq.client.*
import groovy.json.JsonSlurper

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static java.lang.System.exit



///////////////////////////////////
// script arguments management   //
///////////////////////////////////
if (!args || args.length != 1) {
    println "usage: ${this.class.simpleName} /path/to/conf.json"
    exit 1
}

println "${this.class.simpleName} ${args[0]}"
conf = new JsonSlurper().parse(new File(args[0]))
common = conf?.common

// add methods helper for configuration
Object.metaClass.optional = { fields, defaultValue = null, defaultDelegate = null, mandatory = false ->
    def result = fields.split('\\.').inject(delegate) { acc, field ->
        (acc && acc[field] ? acc[field] : null)
    }
    if (result) {
        result
    } else if (defaultDelegate) {
        (defaultDelegate.optional(fields, defaultValue, null, mandatory) ?: defaultValue)
    } else if (!mandatory) {
        println "undefined parameter $fields in $delegate. Use default value '$defaultValue'."
        defaultValue
    } else {
        println "missing mandatory parameter $fields in $delegate"
        exit 1
    }
}

Object.metaClass.mandatory = { fields, defaultDelegate = null -> delegate.optional(fields, defaultValue = null, defaultDelegate = defaultDelegate, mandatory = true) }
Object.metaClass.mandatoryCommon = { fields -> delegate.optional(fields, defaultValue = null, defaultDelegate = common, mandatory = true) }
Object.metaClass.optionalCommon = { fields, defaultValue = null -> delegate.optional(fields, defaultValue, defaultDelegate = common, mandatory = false) }


executor = Executors.newCachedThreadPool()

//////////////////////////////
// consumers initialization //
//////////////////////////////
conf.consumers.each { consumer ->
    def consumerName = consumer.mandatory 'name'
    println "initialize consumer ${consumerName}"

    // Initialize the connection
    def factory = new ConnectionFactory()
    factory.with {
        host = consumer.optionalCommon "host", "localhost"
        port = consumer.optionalCommon "port", Integer.valueOf(5672)
        virtualHost = conf.optional "virtualHost", "/"
        if (consumer.optional("user", null))
            (username, password) = [consumer.optionalCommon("user", null), consumer.optionalCommon("password", null)]
    }

    // create channel, declare exchange and queue
    def channel = factory.newConnection().createChannel()
    def exchangeName = consumer.mandatoryCommon "exchange.name"
    channel.exchangeDeclare(
            exchangeName,
            consumer.optionalCommon("exchange.type", "fanout")
    )
    def queueName = consumer.optionalCommon "queueName", null
    if (!queueName) {
        // declare an anonymous queue
        queueName = channel.queueDeclare().getQueue()
    } else {
        channel.queueDeclare(queueName, true, false, false, null)
    }

    // bind queue to exchange thanks to routing key
    def routingKey = consumer.optionalCommon "routingKey", ""
    channel.queueBind queueName, exchangeName, routingKey

    // receive messages in a different thread
    executor.submit({
        println "[$consumerName] start consuming"
        while (true) {
            def delivery = channel.basicGet(queueName, true)
            if (delivery) {
                println "[$consumerName] receive message '${new String(delivery.body)}' from sender '${delivery.props.getHeaders().sender}'"
            }
            sleep 100
        }
    } as Runnable)
}

//////////////////////////////
//   senders initialization //
//////////////////////////////
def senders = []

conf.senders.each { sender ->
    def senderName = sender.mandatory 'name'
    println "initialize sender ${senderName}"

    // Initialize the connection
    def factory = new ConnectionFactory()
    factory.with {
        host = sender.optionalCommon "host", "localhost"
        port = sender.optionalCommon "port", Integer.valueOf(5672)
        virtualHost = conf.optional "virtualHost", "/"
        if (sender.optional("user", null))
            (username, password) = [sender.optionalCommon("user", null), sender.optionalCommon("password", null)]
    }

    // create channel and declare exchange
    def channel = factory.newConnection().createChannel()
    def exchangeName = sender.mandatoryCommon "exchange.name"
    channel.exchangeDeclare(
            exchangeName,
            sender.optionalCommon("exchange.type", "fanout")
    )
    def routingKey = sender.optionalCommon "routingKey", ""

    // publish messages in a different thread
    senders << executor.submit({
        def count = sender.mandatory "count"
        println "[$senderName] start sending $count messages ..."

        count.times { id ->
            println "[$senderName] send message '$id'"
            channel.basicPublish exchangeName, routingKey, new AMQP.BasicProperties.Builder().headers(["sender": senderName]).build(), "$id".bytes
        }
        println "[$senderName] stop sending messages"
    } as Runnable)
}

// wait for that all senders are finished
senders.each { sender -> sender.get() }

sleep conf.optional("delayKillAfterSendingSeconds", 5) * 1000
exit 0
