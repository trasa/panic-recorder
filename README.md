# Panic! Recorder

An experiment that I started on because I had not done any Android development using Kotlin -- I've only worked with
Unity and C# for mobile development, and even that has been quite a while ago now. This app should be considered
experimental and NOT ready for prime time!

# What is it?

Panic! Recorder is a simple Android app that allows you to quickly start and stop video recordings. These video
files are stored locally as .ts files, which can be played back using VLC or converted to .mp4 files later. The idea
is: you see something interesting happening - you want to record it and you don't have time to find the right app,
camera settings, and all of that. You just open this app and hit "Start Recording". 

Maybe someone doesn't want you to record them. Maybe they'd rather take the phone out of your hand and smash it to pieces.
Thats OK - this app writes in a video format that does not require a closing or finalizing step, so if the app crashes or
the phone is crushed under a boot, the partial video file should still be playable.

But... maybe someone who doesn't want to be recorded goes a step farther and takes the phone from you. This app supports
a streaming mode where the video files are sent to an S3-compatible cloud storage while the recording is taking place.
The video is transmitted in chunks, so even if the camera is destroyed, you hopefully will receive MOST of the data...

Note that you would have had to made those streaming arrangements ahead of time - careful planning is advised!

# Panic Web

This project includes a web application written using .NET and C# which works with your S3 storage to create
presigned urls which the app can use to upload with. You will need to run this web application on some publicly 
accessible server and configure it with the appropriate S3 secrets. Also, you must configure a shared secret
(which both the web app and the Android app have to know) to allow the two to communicate.

# Typical Setup

Personally, I have been using Digital Ocean for my cloud needs. If you are using some other cloud, you shouldn't have
too much trouble making things work there.

I created a Digital Ocean Docker Droplet to host the web application (and an HTTPS frontend using Caddy and LetsEncrypt).
Since there's sensitive information being sent to and from the web app, you need to use HTTPS to secure the information!
(See the docker files for more information on that)

I created a Digital Ocean Space (which is their S3-compatible object storage) and configured a bucket and an access key
that the Web App knows about. The web app generates presigned urls which the Android App can use to stream chunks of a
video file up to that bucket. The Android App has a shared secret with the Web Application to ensure that we don't just
allow ANYBODY to write to our S3 bucket ... it is important to secure this information.

Once you have the Web App and bucket up and running, the Android App has a Settings page where you set the relevant
configuration.

*Note* that S3 support is NOT required to use the web application -- it will write to local storage just fine without it.

# Caveat Emptor

THIS IS AN EXPERIMENTAL APP. It has not been tested on a wide variety of devices or conditions. It might not work.
There is a larger-than-normal amount of work required by a user to get this up and running and tested in all conditions.
Before you go into a potentially dangerous scenario, you should test this app under controlled conditions to see what
happens.
