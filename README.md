### Runescape 414 client patcher
- Replaces the login rsa public key;
- Fixes right mouse click in jdk9+;
- Removes dummy math expressions and impossible conditional jumps;
- Removes the `Packet` class methods variants;

#### Expected client hash:
```
sha256 70b7e2a18529489d72d04ad04a4e9cc0baee2e6463941102f1b0a60aa8d22e3c
```

Example usage:

```
net.oldscape.patcher.Bootstrap --src client.jar --out patched-client.jar
```
