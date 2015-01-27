# rabbitmq-toolbox
some scripts around rabbitmq


# requirements

1. [rabbitmq server](https://www.rabbitmq.com/download.html)
2. [groovy](http://groovy.codehaus.org/Download)
 

# run

With groovy in your path (```/usr/bin/env```):

    $ ./rabbitmq-toolbox.groovy path/to/your/conf.json

Without groovy in your path:

    $ groovy rabbitmq-toolbox.groovy path/to/your/conf.json
    
    
    
# configuration example


You can find samples in directory ```./conf/```:

```json
      {
      "virtualHost": "/",
      "delayKillAfterSendingSeconds": 4,
      "common": {
          "host": "localhost",
          "port": 5672,
          "exchange": {
              "name": "defaultEx",
              "type": "direct"
          },
          "queueName": "myqueue",
          "routingKey": "worker.all"
      },
      "consumers": [
          {
              "name": "worker1"
          },
          {
              "name": "worker2"
          },
          {
              "name": "audit",
              "queueName": "auditQueue1"
          }
      ],
      "senders": [
          {
              "name": "sender",
              "count": 20
          }
      ]
  
  }
```

All parameters in ```common``` element can be overloaded in each ```consumers``` and ```senders```.
