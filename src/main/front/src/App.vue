<template>

<div id="app">

  <Tabs value="t1">

        <TabPane label="ReceiveSendMsg(server -> client)" name="t1">
            <div>
              <div v-for="item in sendS2C" :key="item">{{item}}</div>
            </div>
        </TabPane>

        <TabPane label="ReceivePublishMsg(server -> client)" name="t2">
            <div>
              <div v-for="item in publishS2C" :key="item">{{item}}</div>
            </div>
        </TabPane>

        <TabPane label="SendMsg" name="t3">
          <Button @click="sendMessage">Send message to server </Button>
        </TabPane>

        <TabPane label="PublishMsg" name="t4">
          <Button @click="publicshMessage">Publish message to server</Button>
        </TabPane>

        <TabPane label="RequestMsg" name="t5">
          <Button @click="requestMessage">Reqeust Msg</Button>
        </TabPane>
  </Tabs>


</div>


</template>

<script setup>
import { ref,  getCurrentInstance } from 'vue';
import { onMounted } from 'vue';
import EventBus from './eventbus-plus.js'
const global = getCurrentInstance().appContext.config.globalProperties;

let eb = null;
let sendS2C = ref([])
let publishS2C = ref([])

function sendMessage() {
  eb.send("test-receive-send-c2s", "hello " + new Date )
}

function publicshMessage() {
  eb.publish("test-receive-publish-c2s",  "hello " + new Date() )
}

async function requestMessage() {
  let res = await eb.request("test-receive-c2s-request", "hello ")
  console.log(res)
  global.$Message.info({
    content: res
  })

}

onMounted(() => {
  const loc = window.location;
  eb = new EventBus(loc.href + "eventbus")
  eb.addEventListener('open', () => {
    console.log("eventbus connected!")

    eb.consumer('test-send-s2c', (err, {body})=> {
      sendS2C.value.splice(0, 0, body)
      if(sendS2C.value.length > 10) {
        sendS2C.value.pop()
      }
    })

    eb.consumer('test-publish-s2c', (err, {body})=> {
      publishS2C.value.splice(0, 0, body)
      if(publishS2C.value.length > 10) {
        publishS2C.value.pop()
      }
    })

  })
  
})

</script>