'use strict';

const crypto = require('crypto');

/**
 * Builds the ICE server list a peer should use: STUN always, plus time-limited TURN credentials when a
 * coturn shared secret is configured (coturn `use-auth-secret`). The username is `<expiry>:<id>` and the
 * password is base64(HMAC-SHA1(username, secret)); coturn checks the same HMAC, so no accounts are stored.
 */
function iceServers(config, id) {
  const servers = [];
  if (config.stunUrls.length) servers.push({ urls: config.stunUrls });
  if (config.turnUrls.length && config.turnSecret) {
    const expiry = Math.floor(Date.now() / 1000) + config.turnTtlSeconds;
    const username = `${expiry}:${id || 'dx'}`;
    const credential = crypto.createHmac('sha1', config.turnSecret).update(username).digest('base64');
    servers.push({ urls: config.turnUrls, username, credential });
  }
  return servers;
}

module.exports = { iceServers };
