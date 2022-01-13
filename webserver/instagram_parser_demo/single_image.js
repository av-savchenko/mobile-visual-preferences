function getOrientation(file, callback) {
    const reader = new FileReader();

    reader.onload = function(event) {
        const view = new DataView(event.target.result);

        if (view.getUint16(0, false) != 0xFFD8) return callback(-2);

        let length = view.byteLength,
            offset = 2;

        while (offset < length) {
            const marker = view.getUint16(offset, false);
            offset += 2;

            if (marker == 0xFFE1) {
                if (view.getUint32(offset += 2, false) != 0x45786966) {
                    return callback(-1);
                }
                const little = view.getUint16(offset += 6, false) == 0x4949;
                offset += view.getUint32(offset + 4, little);
                const tags = view.getUint16(offset, little);
                offset += 2;

                for (let i = 0; i < tags; i++)
                    if (view.getUint16(offset + (i * 12), little) == 0x0112)
                        return callback(view.getUint16(offset + (i * 12) + 8, little));
            }
            else if ((marker & 0xFF00) != 0xFF00) break;
            else offset += view.getUint16(offset, false);
        }
        return callback(-1);
    };

    reader.readAsArrayBuffer(file.slice(0, 64 * 1024));
}

function resetOrientation(srcBase64, srcOrientation, callback) {
    const img = new Image();

    img.onload = function() {
        const width = img.width,
            height = img.height,
            canvas = document.createElement('canvas'),
            ctx = canvas.getContext("2d");

        // set proper canvas dimensions before transform & export
        if (4 < srcOrientation && srcOrientation < 9) {
            canvas.width = height;
            canvas.height = width;
        } else {
            canvas.width = width;
            canvas.height = height;
        }

        // transform context before drawing image
        switch (srcOrientation) {
            case 2: ctx.transform(-1, 0, 0, 1, width, 0); break;
            case 3: ctx.transform(-1, 0, 0, -1, width, height ); break;
            case 4: ctx.transform(1, 0, 0, -1, 0, height ); break;
            case 5: ctx.transform(0, 1, 1, 0, 0, 0); break;
            case 6: ctx.transform(0, 1, -1, 0, height , 0); break;
            case 7: ctx.transform(0, -1, -1, 0, height , width); break;
            case 8: ctx.transform(0, -1, 1, 0, 0, width); break;
            default: break;
        }

        // draw image
        ctx.drawImage(img, 0, 0);

        // export base64
        callback(canvas.toDataURL());
    };

    img.src = srcBase64;
}


function download() {
    let submit = true;
    let photo;
    let city;
    if (document.getElementById("city").value) {
        city = document.getElementById("city").value;
    }
    else{
        city = "";
    }


    if (document.getElementById("photo").files[0]) {
        photo = document.getElementById("photo").files[0];
        console.log(document.getElementById("photo").files[0]);
        const reader = new FileReader();

        reader.onload = (function(aImg) {
            return function(e) {
                let file = e.target.result;
                getOrientation(document.getElementById("photo").files[0], function(orientation){
                    orientation=1;
                    resetOrientation(file, orientation, function(resetBase64Image) {
                        aImg.src = resetBase64Image;
                    })
                });
            };
        })(document.getElementById("downloaded_image"));

        reader.readAsDataURL(photo);
    }
    else{
        document.getElementById("error").innerHTML = "Please select an image";
        submit = false;
    }

    if (submit) {
        document.getElementById("error").innerHTML = "";
        document.getElementById("scenes").innerHTML = "";
        document.getElementById("objects").innerHTML = "";
        document.getElementById("restaurants").innerHTML = "";
        document.getElementById("downloaded_image").src = "";
        document.getElementById("loading").src = "loading.gif";
        document.getElementById("loading").width = "64";
        document.getElementById("loading").height = "64";
        sendRequest(photo, city)
            .then(data => {
                document.getElementById("loading").src = "";
                document.getElementById("loading").width = "0";
                document.getElementById("loading").height = "0";

                document.getElementById("scenes").innerHTML = data[0];

                document.getElementById("objects").innerHTML = data[2];

                document.getElementById("restaurants").innerHTML = data[1];
                return 0
            })
            .catch(function(error) {
                document.getElementById("loading").src = "";
                document.getElementById("loading").width = "0";
                document.getElementById("loading").height = "0";
                console.log('Fetch Error!', error);
            });
    }
}


function sendRequest(photo, city) {
    const fData = new FormData();
    fData.append('photo', photo);
    fData.append('city', city);
    return fetch('http://'+location.hostname+':5050/single_photo', {
        method: 'POST',
        body: fData,
    })
        .then(response => response.json());
}