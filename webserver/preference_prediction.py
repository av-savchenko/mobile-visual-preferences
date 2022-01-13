import os
import sys
from collections import OrderedDict

import cv2
import matplotlib
import matplotlib.pyplot as plt
from PIL import Image

plt.rc('xtick', labelsize=7)

from configparser import ConfigParser

configParser = ConfigParser()
configParser.read('pipeline_config.txt')
config = configParser['DEFAULT']

os.environ['TF_MODELS_DIR'] = config['TensorflowModelsPath']
os.environ['CUDA_VISIBLE_DEVICES'] = '0'

import tensorflow as tf

physical_devices = tf.config.experimental.list_physical_devices('GPU')
try:
    tf.config.experimental.set_memory_growth(physical_devices[0], True)
except:
    # Invalid device or cannot modify virtual devices once initialized.
    pass

from facial_analysis.process_photos import process_album, display_demography, is_image, is_video, init_configs
from facial_analysis.facial_analysis import FacialImageProcessing
from detection.detect_objects import ObjectDetector, detect_objects_in_album, display_objects, get_highlevel_objects
from scenes.scene_analysis import recognize_scenes_in_album, get_scene_recognizer, display_histograms, get_histogram, \
    get_highlevel_scenes_histogram, get_scene2files
from yelp.restaurant_analysis import RestaurantProcessing, recognize_restaurants_in_album, get_restaurants2files, \
    RESTAURANT_SCENES
from yelp.restaurant_recommender import RestaurantRecommender


dir_path = os.path.dirname(os.path.abspath(__file__))
new_sys_dir = os.path.join(dir_path, '..')
if not new_sys_dir in sys.path:
    sys.path.append(new_sys_dir)

from scrapers.Instagram.scraper import InstagramScraper

plt.switch_backend('TkAgg')
print('Backend:',matplotlib.get_backend())

class UserPreferences:
    def __init__(self, mostAccurate=True):
        self.minNumberOfObjectsToDisplay = int(config['MinNumberOfObjectsToDisplay'])
        self.minNumberOfScenesToDisplay = int(config['MinNumberOfScenesToDisplay'])
        self.defaultFilterTravelData = int(config['FilterTravelData'])
        self.processVideoFiles = int(config['ProcessVideoFiles'])

        processFaces = int(config['ProcessFaces'])
        if processFaces != 0:
            self.imgProcessing = FacialImageProcessing(print_stat=False, minsize=112)
        else:
            self.imgProcessing = None

        self.object_detector = ObjectDetector(mostAccurate=mostAccurate,
                                              models_path=config['ObjectDetectionModelsPath'])
        self.scene_recognizer = get_scene_recognizer(mostAccurate=mostAccurate)  # False
        self.restaurantProcessing = RestaurantProcessing()
        self.restaurantRecommender=RestaurantRecommender()
        init_configs(config)

    def scrape_instagram(self, username, maximum):
        album = os.path.join(config['InputDirectory'], username)
        if not os.path.exists(album):
            os.makedirs(album)
        scraper = InstagramScraper(usernames=[username], media_types=['image'], maximum=maximum, quiet=False,
                                   destination=album)
        scraper.authenticate_as_guest()
        scraper.scrape()
        return album

    def process_album(self, album, filterTravelData=None, scenesOutfile=None, objectsOutfile=None, restaurantsOutfile=None,city=None):
        title2scenes, title2objects, title2restaurants, title2hotels = None, None, None, None

        if filterTravelData is None:
            filterTravelData = self.defaultFilterTravelData

        image_files = [f for f in next(os.walk(album))[2] if is_image(f)]

        if self.processVideoFiles != 0:
            video_files = [f for f in next(os.walk(album))[2] if is_video(f)]
        else:
            video_files = []

        scenes = recognize_scenes_in_album(self.scene_recognizer, album, image_files, video_files)
        scene2files = get_scene2files(self.scene_recognizer, scenes, album, save_events=False)
        scene2count = get_histogram(scene2files, min_count=self.minNumberOfScenesToDisplay)
        if scene2count is not None and len(scene2count) > 0:
            if filterTravelData == 0:
                title2scenes = {'Scenes': scene2count}
            else:
                title2scenes = get_highlevel_scenes_histogram(self.scene_recognizer.highlevelCategories, scene2count)

            display_histograms(title2scenes, scenesOutfile)

        restaurants_file2scores = recognize_restaurants_in_album(self.restaurantProcessing, album, scene2files)
        restaurants_model2files = get_restaurants2files(self.restaurantProcessing, restaurants_file2scores)
        title2restaurants={}
        
        for modelname, model2files in restaurants_model2files.items():
            restaurants2count = get_histogram(model2files, min_count=self.minNumberOfScenesToDisplay)
            if restaurants2count is not None and len(restaurants2count) > 0:
                title2restaurants[modelname] = restaurants2count        
        if len(title2restaurants) > 0:
            print(title2restaurants)
            display_histograms(title2restaurants, restaurantsOutfile)
            
            #recommendedRestaurants=self.restaurantRecommender.recommend_for_album(restaurants_file2scores, city=city)
            recommendedRestaurants=self.restaurantRecommender.recommend_for_album_histo(title2restaurants['YELP cuisine'], city=city)
            #print(recommendedRestaurants);sys.exit(0)
        else:
            recommendedRestaurants=None


        file2objects, album_face_bboxes, video_bboxes = detect_objects_in_album(self.object_detector, album,
                                                                                image_files,
                                                                                video_files)

        objects_count = {}
        for path in file2objects:
            img_objects = file2objects[path]
            for category in img_objects:
                if category not in objects_count:
                    objects_count[category] = 1
                else:
                    objects_count[category] += 1
        frequent_objects = OrderedDict(
            [(self.object_detector.category_index[obj]['name'], float(objects_count[obj])) for obj in
             sorted(objects_count, key=objects_count.get, reverse=True) if
             objects_count[obj] >= self.minNumberOfObjectsToDisplay])
        print('\nfrequent objects from %d images and %d videos:' % (len(image_files), len(video_files)))
        if filterTravelData == 0:
            title2objects = {'Objects': frequent_objects}
        else:
            title2objects = get_highlevel_objects(self.object_detector.highlevelCategories, frequent_objects)
        display_objects(title2objects, objectsOutfile)

        if self.imgProcessing is not None:
            _, _, cluster_genders, cluster_born_years = process_album(self.imgProcessing, album,
                                                                      save_facial_clusters=False,
                                                                      save_public_photos=False, no_clusters_to_show=3,
                                                                      album_face_bboxes=album_face_bboxes,
                                                                      video_bboxes=video_bboxes)
            print('Demography:')
            display_demography(cluster_genders, cluster_born_years)

        return title2scenes, title2objects, title2restaurants, recommendedRestaurants

    def analyze_image(self, filename,city=''):
        img = cv2.imread(filename)
        image = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

        scenes = self.scene_recognizer.recognize_scene_event(img)

        # print(scenes,RESTAURANT_SCENES,HOTEL_SCENES)
        isRestaurant = False
        for restaurant_scene in RESTAURANT_SCENES:
            if restaurant_scene in scenes:
                isRestaurant = True
                break
        restaurants_model2descr = self.restaurantProcessing.recognize(img)
        restaurants = {modelname: ('<br>'.join(descr[0]) if isinstance(descr[0], list) else descr[0]) for modelname, descr in
                       restaurants_model2descr.items()}

        recommendedRestaurants=None
        if isRestaurant:
            cuisine_prediction=self.restaurantProcessing.get_cuisine_prediction(restaurants_model2descr)
            recommendedRestaurants=self.restaurantRecommender.recommend(cuisine_prediction, city=city)
            #print(recommendedRestaurants)

        output_dict = self.object_detector.detect_objects(image)
        objects = '<br>'.join(['%s:%.3f' % (self.object_detector.category_index[class_ind]['name'], score)
                               for class_ind, score in
                               zip(output_dict['detection_classes'], output_dict['detection_scores']) if
                               (class_ind + 1) not in self.object_detector.FILTERED_INDICES])


        return scenes, isRestaurant, restaurants, recommendedRestaurants, objects

    def get_cities(self):
        return self.restaurantRecommender.get_cities()

    def close(self):
        if self.imgProcessing is not None:
            self.imgProcessing.close()

        self.object_detector.close()
        self.scene_recognizer.close()
