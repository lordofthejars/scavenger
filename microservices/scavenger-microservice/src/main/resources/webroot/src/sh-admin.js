(() => {
  const websocketUrl = location.hostname === 'localhost' ? 'ws://localhost:8080/admin' : 'wss://scavenger-hunt-microservice-scavenger-hunt-microservice.apps.workspace7.org/admin';
  const socket = new WebSocket(websocketUrl);
  const adminToken = 'dGhpc19pc19hX3NlY3JldF9zdW1taXRfMTg=';
  const gameStateSelect = document.querySelector('#gameState');

  socket.onopen = onOpen;
  socket.onclose = onClose;
  socket.onmessage = onMessage();

  gameStateSelect.addEventListener('change', gameStateSelectHandler);

  function onOpen() {
    console.log('Websocket connected');
  }

  function onClose() {
    console.log('Websocket closed');
  }

  function onMessage(event) {
    console.log(event);
    if (!event || !event.data) {
      return;
    }

    const data = JSON.parse(event.data);
    console.log(data);
  }

  function sendMessage(message) {
    if (socket.readyState !== socket.OPEN) {
      console.log('Unable to send message. The websocket is not open');
      return;
    }

    socket.send(JSON.stringify(message));
  }

  function gameStateSelectHandler(event) {
    const state = event.target.value;
    const message = {
      "type": "game",
      "token": adminToken,
      "state": state
    }

    sendMessage(message);

    // need to hide the select field for 5 seconds because the
    // websocket will send an updated configuration frame over
    // a 5 second period (1 configuration frame every second for
    // 5 seconds)
    gameStateSelect.setAttribute('disabled', true);

    setTimeout(() => {
      gameStateSelect.removeAttribute('disabled');
    }, 5000);
  }
})();
