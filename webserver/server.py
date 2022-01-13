import sys
from subprocess import call
from flask_cors import CORS
from flask import Flask, jsonify, send_file, Response
from flask import request
import json
import argparse
import os
import requests
import matplotlib.pyplot as plt
import numpy as np
import io

plt.switch_backend('agg')
plt.rc('xtick', labelsize=7)

from preference_prediction import UserPreferences,config
from scenes.scene_analysis import get_scene_recognizer

app = Flask(__name__)
CORS(app)

TMP_DIR = 'tmp'  # FIXME: fix it if it is unexpected. get_image function is using this constant.
SCENES_FILE = 'scenes_hist.png'
OBJECTS_FILE = 'objects_hist.png'
RESTAURANTS_FILE = 'restaurants_hist.png'


def main(username, maximum, filterTravelData, city):
    album = preferences.scrape_instagram(username, maximum)
    if os.path.exists(SCENES_FILE):
        os.remove(SCENES_FILE)
    if os.path.exists(OBJECTS_FILE):
        os.remove(OBJECTS_FILE)
    if os.path.exists(RESTAURANTS_FILE):
        os.remove(RESTAURANTS_FILE)
    return preferences.process_album(album, filterTravelData=filterTravelData, scenesOutfile=SCENES_FILE,
                                     objectsOutfile=OBJECTS_FILE, restaurantsOutfile=RESTAURANTS_FILE, city=city)


@app.route('/')
def status_check():
    return 'Server is running'


def run_inference(received_args):
    return main(received_args['userName'], received_args['numberOfPhotos'], received_args['FilterTravelData'], received_args['city'])



def restaurants_info_formatting(restaurants):
    s = "<br><b>Recommended restaurants</b><br>"\
        "<table style=\"border: none\" align=\"center\"><tr><th>Name</th><th>Cuisine</th><th>Stars</th></tr>"
    for row in restaurants:
        s += "<tr><td><a href=\"https://www.google.com/maps/place/"+str(row[3])+","+str(row[4])+"\" target=\"_blank\">"+row[0]+"</a></td><td>"+row[1]+"</td><td>"+str(row[5])+"</td></tr>"
    s += "</table>"
    return s

DEFAULT_PHOTO_PATH='test.jpg'
def save_content_to_file(content,filename=DEFAULT_PHOTO_PATH):
    with open(filename, 'wb') as f:
        f.write(io.BytesIO(content).getvalue())

def single_photo_inference(photo,city):
    save_content_to_file(photo, DEFAULT_PHOTO_PATH)
    scenes, isRestaurant, restaurants_dic, recommendedRestaurants, objects = preferences.analyze_image(DEFAULT_PHOTO_PATH,city)
    scenes = "<b>" + "Scenes/Events" + "</b><br>" + scenes
    restaurants = "" if isRestaurant else "<i>WARNING! Not a restaurant</i> <br>"
    for modelname,description in restaurants_dic.items():
        restaurants += "<b>" + modelname + "</b><br>" + description+"<br>"
    if recommendedRestaurants is not None and len(recommendedRestaurants)>0:
        restaurants+=restaurants_info_formatting(recommendedRestaurants)
    
    objects = "<b>" + "Objects" + "</b><br>" + objects
    
    print(scenes, restaurants, objects)

    return scenes, restaurants, objects


@app.route('/instagram_parser', methods=['POST'])
def process_image():
    content = request.get_json()
    scenes, objects, restaurants, recommendedRestaurants = run_inference(content)
    if recommendedRestaurants is not None and len(recommendedRestaurants)>0:
        recommended_restaurants_info=restaurants_info_formatting(recommendedRestaurants)
    else: 
        recommended_restaurants_info=''
    return Response(response=json.dumps([scenes, objects, restaurants, recommended_restaurants_info]),
                    status=200,
                    mimetype="application/json")


@app.route('/single_photo', methods=['POST'])
def process_photo():
    photo = request.files['photo'].read()
    city=request.form['city']
    scenes, objects, restaurants = single_photo_inference(photo,city)
    return Response(response=json.dumps([scenes, objects, restaurants]),
                    status=200,
                    mimetype="application/json")


@app.route('/scenes_hist', methods=['POST'])
def scenes_hist():
    if not os.path.exists(SCENES_FILE):
        return json.dumps({'error': 'scenes file does not exist'}), 500
    else:
        return send_file(SCENES_FILE, mimetype='image/png'), 200



@app.route('/restaurants_hist', methods=['POST'])
def restaurants_hist():
    if not os.path.exists(RESTAURANTS_FILE):
        return json.dumps({'error': 'restaurants file does not exist'}), 500
    else:
        return send_file(RESTAURANTS_FILE, mimetype='image/png'), 200

@app.route('/objects_hist', methods=['POST'])
def objects_hist():
    if not os.path.exists(OBJECTS_FILE):
        return json.dumps({'error': 'objects file does not exist'}), 500
    else:
        return send_file(OBJECTS_FILE, mimetype='image/png'), 200


if __name__ == '__main__':
    preferences = UserPreferences()
    app.run(host="0.0.0.0", port=5050)
