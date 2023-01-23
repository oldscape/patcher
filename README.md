### Runescape 414 client patcher
- Replaces the login rsa public key;
- Fixes right mouse click in jdk9+;
- Removes dummy math expressions and impossible conditional jumps;
- Removes the `Packet` class methods variants;

### RSA keys

If you have an RSA private key (PCKS8 format) you can place it in the project root directory and name it `login-key.key`, 

Example of usage:

```
net.oldscape.patcher.Bootstrap --src 414/client.jar --out 414/patched-client.jar
```
