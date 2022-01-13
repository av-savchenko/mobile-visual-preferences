function download() {
    let submit = true;
    let userName;
    let numberOfPhotos;

    if (document.getElementById("username").value) {
        userName = document.getElementById("username").value;
    }
    else{
        document.getElementById("error").innerHTML = "Please enter a username";
        submit = false;
    }

    if (document.getElementById("number_of_photos").value) {
        numberOfPhotos = parseInt(document.getElementById("number_of_photos").value);
    }
    else{
        numberOfPhotos = 100;
    }

    let FilterTravelData = parseInt(document.getElementById("FilterTravelData").value);
    
    let city;
    if (document.getElementById("city").value) {
        city = document.getElementById("city").value;
    }
    else{
        city = "";
    }


    if (submit) {
        document.getElementById("error").innerHTML = "";
        document.getElementById("scenes").innerHTML = "";
        document.getElementById("objects").innerHTML = "";
        document.getElementById("restaurants").innerHTML = "";
        document.getElementById("scenes_hist").src = "";
        document.getElementById("objects_hist").src = "";
        document.getElementById("restaurants_hist").src = "";
        document.getElementById("loading").src = "loading.gif";
        document.getElementById("loading").width = "64";
        document.getElementById("loading").height = "64";
        sendRequest(userName, numberOfPhotos, FilterTravelData, city)
            .then(data => {
                document.getElementById("loading").src = "";
                document.getElementById("loading").width = "0";
                document.getElementById("loading").height = "0";
                if (FilterTravelData === 0) {
                    let scenes = data[0]["Scenes"];
                    let txt = "<b>" + "Scenes" + "</b><br>";
                    for (let x in scenes) {
                        console.log(scenes[x]);
                        txt += x + ": " + scenes[x] + "<br>";
                    }
                    document.getElementById("scenes").innerHTML = txt;

                    let objects = data[1]["Objects"];
                    txt = "<b>" + "Objects" + "</b><br>";
                    for (let x in objects) {
                        console.log(x);
                        console.log(objects[x]);
                        txt += x + ": " + objects[x] + "<br>";
                    }
                    document.getElementById("objects").innerHTML = txt;
                }
                else {
                    let scenes = data[0];
                    let txt = "<b>" + "SCENES" + "</b><br><br>";
                    for (let x in scenes) {
                        txt += "<b>" + x + "</b><br>";
                        for (let y in scenes[x]) {
                            txt += y + ": " + scenes[x][y] + "<br>";
                        }
                    }
                    document.getElementById("scenes").innerHTML = txt;

                    let objects = data[1];
                    txt = "<b>" + "OBJECTS" + "</b><br><br>";
                    for (let x in objects) {
                        txt += "<b>" + x + "</b><br>";
                        for (let y in objects[x]) {
                            txt += y + ": " + objects[x][y] + "<br>";
                        }
                    }
                    document.getElementById("objects").innerHTML = txt;
                }
                let restaurants = data[2];
                txt = "<br><br>";//"<b>" + "YELP label classification" + "</b><br><br>";
                for (let x in restaurants) {
                    txt += "<b>" + x + "</b><br>";
                    for (let y in restaurants[x]) {
                        txt += y + ": " + restaurants[x][y] + "<br>";
                    }
                }
                let recommended_restaurants = data[4];
                txt+=recommended_restaurants
                document.getElementById("restaurants").innerHTML = txt;

                return 0
            })
.then(() => {
                getHistograms("scenes_hist")
                    .then(response => {
                        if (response.status === 200) {
                            return response.blob();
                        }
                        else {
                            return 'Fetch Error!';
                        }
                    })
                    .then(images => {
                        if (images !== 'Fetch Error!'){
                            document.getElementById("scenes_hist").src = URL.createObjectURL(images);
                        }
                    })
            })
            .then(() => {
                getHistograms("objects_hist")
                    .then(response => {
                        if (response.status === 200) {
                            return response.blob();
                        }
                        else {
                            return 'Fetch Error!';
                        }
                    })
                    .then(images => {
                        if (images !== 'Fetch Error!'){
                            document.getElementById("objects_hist").src = URL.createObjectURL(images);
                        }
                    })
            })
            .then(() => {
                getHistograms("restaurants_hist")
                    .then(response => {
                        if (response.status === 200) {
                            return response.blob();
                        }
                        else {
                            return 'Fetch Error!';
                        }
                    })
                    .then(images => {
                        if (images !== 'Fetch Error!'){
                            document.getElementById("restaurants_hist").src = URL.createObjectURL(images);
                        }
                    })
            })
            .catch(function(error) {
                document.getElementById("loading").src = "";
                document.getElementById("loading").width = "0";
                document.getElementById("loading").height = "0";
                console.log('Fetch Error!', error);
            });
    }
}


function sendRequest(userName, numberOfPhotos, FilterTravelData, city) {
    return fetch('http://'+location.hostname+':5050/instagram_parser', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            "userName": userName,
            "numberOfPhotos": numberOfPhotos,
            "FilterTravelData": FilterTravelData,
            "city":city
        })
    })
        .then(response => response.json());
}


function getHistograms(type) {
    if (type === "objects_hist") {
        return fetch('http://'+location.hostname+':5050/objects_hist', {
            method: 'POST',
            headers: {
                'Content-Type': 'image/png'
            },
        })
    }
    else if (type === "restaurants_hist") {
        return fetch('http://'+location.hostname+':5050/restaurants_hist', {
            method: 'POST',
            headers: {
                'Content-Type': 'image/png'
            },
        })
    }
    else {
        return fetch('http://'+location.hostname+':5050/scenes_hist', {
            method: 'POST',
            headers: {
                'Content-Type': 'image/png'
            },
        })
    }
}
