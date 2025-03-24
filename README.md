# Panic Recorder

If the app is interrupted before you end recording, the mp4 is damaged.
To fix:

```
ffmpeg -i broken.mp4 -c copy repaired.mp4
```

