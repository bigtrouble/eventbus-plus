/* eslint-disable */
EventBus.CONNECTING = 0
EventBus.OPEN       = 1
EventBus.CLOSING    = 2
EventBus.CLOSED     = 3

function uuidv4() {
  return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, c =>
    (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
  );
}

function EventBus(url) {

  this._lazyInitConsumer = []
  this._send = obj => {
    let payload = JSON.stringify(obj)
    this.socket.send(payload);
  }
  this._toWS = path => {
    let org = path.startsWith("https://") || path.startsWith("http://") ? path : window.location.origin + path;
    org = org.replace("https://", "wss://")
    org = org.replace("http://", "ws://")
    return org
  }

  this._init = (url) => {

    this.handlers = {}
    this.replyHandlers = {}
    this.trackHandlers = {}
    this.state = EventBus.CONNECTING
    this.socket = new WebSocket(this._toWS(url))
    this.socket.addEventListener('open',  () => this.state = EventBus.OPEN)
    this.socket.addEventListener('message', async evt => {

        const msg = JSON.parse(evt.data)

        if (msg.type === 'pong') {
          return
        }

        if (msg.type === 'done') {
            console.log('[eventbus] received connection done flag.')
            this.pingTimerID = setInterval(() => this.socket.send(JSON.stringify({type: 'ping'})), 5000);
            for(const callback of this.openHandlers) {
                callback.call(null, this)
            }
            while(this._lazyInitConsumer.length) {
                const {address, callback, resolve} = this._lazyInitConsumer.shift();
                if (address) {
                const rr = await this.consumer(address, callback);
                resolve(rr)
                console.log('register consumer: ', address, ' lazy done.');
                }
            }
            this.onopen && this.onopen()
            return
        }
  
      
      if(msg.address) {
        const needReply = !!msg.replyAddress
        let replySent = false
        if(this.handlers[msg.address]) {
          let handlers = this.handlers[msg.address];
          for (let i = 0; i < handlers.length; i++) {
            const r = handlers[i].callback(null, msg, handlers[i]);
            if(needReply) {
              if(!replySent) {
                this.send(msg.replyAddress, r);
                replySent = true
              } else {
                console.warn('This message has been replied. Because there are multiple handlers! address:', address);
              }
            }
          }

          if(needReply && !replySent) {
            console.warn('No reply to message! address:', address);
          }
        }
      }
      else if(msg.replyAddress) {
        if(this.replyHandlers[msg.replyAddress]) {
          let handler = this.replyHandlers[msg.replyAddress]
          delete this.replyHandlers[msg.replyAddress]
          if(typeof handler === 'object') {
            if (msg.err) {
              handler.reject(msg)
            } else {
              handler.resolve(msg.body)
            }
          }
          else if(typeof handler === 'function') {
            if (msg.err) {
              handler({ message: msg.message, code: msg.failureCode, type: msg.failureType })
            } else {
              handler(null, msg.body)
            }
          } else {
            console.error('??' , msg, handler)    
          }
        }
      }
      else if (msg.trackId) {
        const tick = this.trackHandlers[msg.trackId]
        delete this.trackHandlers[msg.trackId]
        if (tick) {
          tick.resolve(tick.ret)
        } else {
          console.error('track:' , msg)
        }
      }
      else {
        console.error('??' , msg)
      }
    })
  
    this.socket.addEventListener('close', evt => {
      this.state = EventBus.CLOSED;
      if(this.pingTimerID) {
        window.clearInterval(this.pingTimerID)
        this.pingTimerID = 0
      }
      this.onclose && this.onclose(evt);

      for(const callback of this.closeHandlers) {
        callback.call(null, this, evt)
      }

      if(!this.__close) {
        setTimeout(() => {
          this._init(url)
          console.log('EventBus Retrying....')
        }, 1000)
      }
    })
  }
  this.openHandlers = new Set()
  this.closeHandlers = new Set()
  this._init(url)
}


/**
 * add listener for ws open/close
 *
 * @param {String} type
 * @param {Function} callback
 */
EventBus.prototype.addEventListener = function (type, callback) {
  switch(type) {
    case 'open':
      this.openHandlers.add(callback)
      break;
    case 'close':
      this.closeHandlers.add(callback)
      break;
    default: {
      console.log('Invalid type!', type)
    }
  }
}


/**
 * remove ws open/close event.
 *
 * @param {String} type
 * @param {Function} callback
 */
EventBus.prototype.removeEventListener = function (type, callback) {
  switch(type) {
    case 'open':
      this.openHandlers.delete(callback)
      break;
    case 'close':
      this.closeHandlers.delete(callback)
      break;
    default: {
      console.log('Invalid type!', type)
    }
  }
}



/**
 * Send a message
 *
 * @param {String} address
 * @param {Object} body
 * @param {Object} [headers]
 */
EventBus.prototype.send = function (address, body, headers) {
  if (this.state !== EventBus.OPEN) {
    throw new Error('INVALID_STATE_ERR');
  }
  let envelope = {
    type: 'send',
    address,
    headers,
    body
  };
  this._send(envelope);
}


/**
 * Request a message
 *
 * @param {String} address
 * @param {Object} body
 * @param {Object} [headers]
 * @param {Function} callback
 */
EventBus.prototype.request = function (address, body, headers, callback) {
  if (this.state !== EventBus.OPEN) {
    throw new Error('INVALID_STATE_ERR');
  }
  if (typeof headers === 'function') {
    callback = headers;
    headers = {};
  }
  let envelope = {
    type: 'send',
    address,
    headers,
    body
  };



  const replyAddress = `reply-${uuidv4()}`
  envelope.replyAddress = replyAddress;
  let promise = null;
  if (callback) {
    this.replyHandlers[replyAddress] = callback;
  } else {
    promise = new Promise((resolve, reject) => {
      this.replyHandlers[replyAddress] = {
        resolve,
        reject
      }
    })
  }
  this._send(envelope);
  return promise
};



/**
 * Publish a message
 *
 * @param {String} address
 * @param {Object} body
 * @param {Object} [headers]
 */
 EventBus.prototype.publish = function (address, body, headers) {
  if (this.state !== EventBus.OPEN) {
    throw new Error('INVALID_STATE_ERR');
  }
  this._send({
    type: 'publish',
    address,
    headers,
    body
  })
}

    
/**
 * Register a new handler
 *
 * @param {String} address
 * @param {Function} callback
 */
EventBus.prototype.consumer = async function (address, callback) {
  if (this.state !== EventBus.OPEN) {
    return new Promise((resolve, reject) => {
      this._lazyInitConsumer.push({
        address, callback, resolve, reject
      })
    })
  } else {
    const ret = {
      address,
      callback,
      unregister: () => this.unregisterHandler(address, callback)
    }
    if (!this.handlers[address]) {
      this.handlers[address] = [];
      const trackId = `t-${uuidv4()}`
      const promise = new Promise((resolve, reject) => {
        this.trackHandlers[trackId] = {
          resolve,
          reject,
          ret
        }
      })
      this._send({
        type: 'register',
        address,
        trackId
      })
      this.handlers[address].push(ret)
      return promise;
    } else {
      this.handlers[address].push(ret)
      return Promise.resolve(ret)
    }
  }
}



/**
 * Unregister a handler
 *
 * @param {String} address
 * @param {Function} callback
 */
EventBus.prototype.unregisterHandler = function (address, callback) {
  const handlers = this.handlers[address];
  if (handlers) {
    const idx = handlers.map(r => r.callback).indexOf(callback);
    if (idx !== -1) {
      handlers.splice(idx, 1);
      if (handlers.length === 0) {
        if (this.state === EventBus.OPEN) {
          this._send({type: 'unRegister',address})
        }
        delete this.handlers[address]
      }
    }
  }
}


/**
 * close
 */
EventBus.prototype.close = function () {
  this.state = EventBus.CLOSING;
  this.__close = true
  this.socket.close()
  this.handlers = {}
  this.replyHandlers = {}
  this.closeHandlers.clear()
  this.openHandlers.clear()
  this.trackHandlers = null
}



/**
 * uuid
 */
EventBus.prototype.uuid = function () {
  return uuidv4()
}


export default EventBus
