### How to set up the instagram parser demo:

Run the following command from the **webserver** directory to launch the Flask server:

```
python server.py
```

Run the following command from the **webserver/instagram_parser_demo/** directory:

```
python -m http.server 5555
```

Now you can go to http://localhost:5555/ in your browser at the same host. You will be automatically redirected to the web server containing the demo for the instagram parser.

If a remote connection is required to the hostname/IPAddress 'ip.address' and only ssh connection is available, run the following commands to configure SSH port forwarding in a separate tab of the terminal f your local computer:

```
ssh -NL localhost:5555:localhost:5555 username@ip.address -p [port]
```
```
ssh -NL localhost:5050:localhost:5050 username@ip.address -p [port]
```

Here username and port are the user name and port number of SSH connection. At this point it is possible to go to http://localhost:5555/ of your local machine.