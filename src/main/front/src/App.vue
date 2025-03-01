<template>

  <div style="display: flex;">
    <button @click="send(1234)">send number</button>
    <button @click="send(  ({'aa': 'cc'}) )">send object</button>
    ---
    <button @click="publish(12341234)">publish number</button>
    <button @click="publish({'dd': 1234})">publish object</button>
  </div>

</template>

<script setup>
import { onMounted } from 'vue';
import EventBus from './eventbus-plus.js'

let eb = null;


function send(data) {
  eb.send("abcd-1", data)
}

function publish(data) {
  eb.publish("abcd-2", data)
}

onMounted(() => {
  const loc = window.location;
  eb = new EventBus(loc.href + "kiabus/eventbus")
  eb.consumer("abcd", (err, {body}) => {
    console.log(body)
  })
  
})

</script>