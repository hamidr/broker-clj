'use strict'
const WebSocket = require('ws')
const pj = require('prettyjson')
const uuid = require('node-uuid');

const api_key = '24778611501d2898a754a681ae32e4b16038b34'
const user_id = '56548b08e1382319cc000002'

function randomIntInc (low, high) {
  return Math.floor(Math.random() * (high - low + 1) + low);
}


var numbers = new Array(100);
for (var i = 0; i < numbers.length; i++) {
  numbers[i] = randomIntInc(1,100)
}


function randEA(array) {
  return array[Math.floor(Math.random()*array.length)];
}



var connections = [];
for(var n = 0; n <= 10; ++n) {
    var ws = new WebSocket('ws://localhost:9090/ws');

    ws.send_json = function(json) {
      this.send(JSON.stringify(json));
    };

    ws.on('open', function() {
        this.send_json({ 
            command:  "authenticate",
            username: uuid.v4(),
            password: "password",
            //app_key: "925f1d712e248ec3a683b6b33b28f8cafa7682f"
        })
        this.hellos = 0;
    })

    ws.on('close', function() {
        console.log('closed')
    })

    ws.on('message', function (data) {

      var self = this;
      var msg = JSON.parse(data)
        switch(msg.reaction) {
          case "authenticated":

                //id: "56b2033ee1382341a9000010",
            this.send_json({
                command: "listen",
                id: ""+randEA(numbers),
                //id: "56b76940e138231ba7000000",
                version: "1",
                topic: randEA(["user", "project"])

            });
            this.send_json({
              command: "hey",
            })

          case 'hi':
            break;
            this.send_json({
              command: "typing",
              id: ""+randEA(numbers),
              version: randEA(["0","1"]),
              topic: randEA(["user", "project"])
            });

            break;

        default:
       }
      console.log(msg)
    })
    connections.push(ws);
}

