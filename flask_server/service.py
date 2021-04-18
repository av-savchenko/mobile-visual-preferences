from module import app
from module.constants import SSL_PATH
from module.constants import HOST
from os import path
from werkzeug.serving import make_ssl_devcert

# if not path.exists(SSL_PATH + '.crt'):
    # make_ssl_devcert(SSL_PATH, host=HOST)

if __name__ == "__main__":
    print("* Flask starting server...")
    app.run(
        host='0.0.0.0',
        # ssl_context=(SSL_PATH + '.crt', SSL_PATH + '.key'),
        debug=True)
