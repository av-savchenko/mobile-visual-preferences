from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import sys
import os
import argparse
import numpy as np
import cv2
import time
import pickle
import math
from collections import OrderedDict, ChainMap

import matplotlib.pyplot as plt

from tensorflow.keras.models import load_model
from tensorflow.keras.applications import mobilenet_v2, mobilenet, inception_v3
import efficientnet.tfkeras as enet

net_model=mobilenet_v2
net_model2=inception_v3

dir_path = os.path.dirname(os.path.abspath(__file__))
new_sys_dir = os.path.join(dir_path,'..')
if not new_sys_dir in sys.path:
    sys.path.append(new_sys_dir)

from facial_analysis.process_photos import is_image
    
RESTAURANT_SCENES=['archive','bakery shop','bar','beer hall','beer garden','candy store', 'cafeteria','coffee shop','delicatessen',
             'diner','dining room','dining hall','fastfood restaurant','food court','general store','ice cream parlor','picnic','pizza','pub',
             'restaurant','restaurant kitchen','restaurant patio','shopfront','supermarket','sushi bar','wet bar','Oktoberfest']

#RESTAURANT_CUISINES= = [ 'Sandwiches', 'Fast Food', 'American (Traditional)', 'Pizza',  'Breakfast & Brunch', 'American (New)', 'Italian', 'Mexican', 'Chinese', 'Coffee & Tea', 'Japanese', 'Chicken Wings', 'Salad', 'Seafood', 'Sushi Bars', 'Specialty Food', 'Delis', 'Asian Fusion', 'Canadian (New)', 'Bakeries', 'Mediterranean','Indian']#, 'Caterers', 'Barbeque', 'Sports Bars', 'Desserts', 'Steakhouses', 'Pubs', 'Thai', 'Diners', 'Middle Eastern', 'Vietnamese', 'Wine & Spirits', 'Beer', 'Vegetarian', 'Greek', 'French', 'Ice Cream & Frozen Yogurt', 'Cocktail Bars', 'Wine Bars', 'Korean', 'Juice Bars & Smoothies', 'Buffets', 'Ethnic Food', 'Comfort Food', 'Gluten-Free', 'Vegan']#, 'Soup', 'Hot Dogs', 'Tex-Mex', 'Caribbean', 'Halal', 'Latin American', 'Southern', 'Tapas/Small Plates', 'Pakistani', 'Noodles', 'Bagels', 'Tapas Bars', 'Beer Bar', 'Hawaiian', 'Fish & Chips', 'Donuts', 'Soul Food']
RESTAURANT_CUISINES = [ 'Sandwiches', 'Fast Food', 'American (Traditional)', 'Pizza',  'Breakfast & Brunch', 'American (New)', 'Italian', 'Mexican', 'Chinese', 'Coffee & Tea', 'Japanese', 'Seafood', 'Sushi Bars', 'Asian Fusion', 'Canadian (New)']

class ClassifierParams:
    def __init__(self, threshold, multiclass, labels):
        self.threshold=threshold
        self.multiclass=multiclass
        self.labels=labels

class ClassifierModel:
    def __init__(self, model_file, model_class, params):
        src_file_path,_ = os.path.split(os.path.realpath(__file__))
        self.model_file=model_file
        self.model=load_model(os.path.join(src_file_path,'..','models','yelp',model_file+'.h5'))
        self.input_size=self.model.input_shape[1:3]
        self.preprocess_input=model_class.preprocess_input
        self.params=params
    
    def predict(self,img):
        resized_image = cv2.resize(img, (self.input_size))
        inp = self.preprocess_input(np.expand_dims(resized_image, axis=0).astype(np.float32))
        preds=self.model.predict(inp)[0]
        return preds   
    
    def get_description(self,preds):
        if self.params.multiclass:
            indices, = np.where(np.array(preds) > self.params.threshold)
            label, score = [self.params.labels[i] for i in indices], [preds[i] for i in indices]
            return ['%s (%.2f)'%(label_, score_) for label_, score_ in zip(label, score)]
        else:
            indices=preds.argsort()[::-1]
            label, score = self.params.labels[indices[0]],preds[indices[0]]
            return '%s (%.2f)'%(label, score)            

    def recognize(self, img_rgb, print_time=True):
        t = time.time()
        preds = self.predict(img_rgb)
        elapsed = time.time() - t
        if print_time:
            print('Restaurant recognition for ', self.model_file, ' elapsed', elapsed)
        return self.get_description(preds), preds


class MultiHeadClassifierModel:
    def __init__(self, model_file, model_class, output_params):
        src_file_path,_ = os.path.split(os.path.realpath(__file__))
        self.model_file=model_file
        self.model=load_model(os.path.join(src_file_path,'..','models','yelp',model_file+'.h5'))
        self.input_size=self.model.input_shape[1:3]
        self.preprocess_input=model_class.preprocess_input
        self.output_params=output_params
    
    def predict(self,img):
        resized_image = cv2.resize(img, (self.input_size))
        inp = self.preprocess_input(np.expand_dims(resized_image, axis=0).astype(np.float32))
        preds=self.model.predict(inp)
        return [pred[0] for pred in preds]
    
    def get_description(self,preds,params):
        if params.multiclass:
            indices, = np.where(np.array(preds) > params.threshold)
            label, score = [params.labels[i] for i in indices], [preds[i] for i in indices]
            return ['%s (%.2f)'%(label_, score_) for label_, score_ in zip(label, score)]
        else:
            indices=preds.argsort()[::-1]
            label, score = params.labels[indices[0]],preds[indices[0]]
            return '%s (%.2f)'%(label, score)            

    def recognize(self, img_rgb, print_time=True):
        t = time.time()
        preds = self.predict(img_rgb)
        elapsed = time.time() - t
        if print_time:
            print('Restaurant recognition for ', self.model_file, ' elapsed', elapsed)
        return {output_param[0]:(self.get_description(pred,output_param[1]), preds) for pred,output_param in zip(preds,self.output_params)}
             
class RestaurantProcessing:
    def __init__(self,threshold1=0.5, threshold2=0.9, threshold3=0.15):
        params1=ClassifierParams(threshold1, multiclass=False, labels = ['drink', 'food', 'inside', 'menu', 'outside'])
        params2=ClassifierParams(threshold2, multiclass=True, labels = ['good for lunch', 'good for dinner', 'takes reservations', 'outdoor seating',
                            'expensive restaurant', 'has alcohol', 'has table service', 'ambience is classy', 'good for kids'])
        params3=ClassifierParams(threshold3, multiclass=True, labels = RESTAURANT_CUISINES)

        self.models={}
        #self.models['YELP labels']=ClassifierModel(model_file='mobilenet2_alpha1_feats_ft_sgd', model_class=mobilenet_v2, params=params1)
        #self.models['YELP cuisine']=ClassifierModel(model_file='enet3_multioutput_model_augm_ft_sgd', model_class=enet, params=params3)        
        
        #model_file,model_class='mobilenet_restaurants',mobilenet_v2
        #model_file,model_class='inception_restaurants',inception_v3
        #self.models['YELP restaurants']=ClassifierModel(model_file=model_file, model_class=model_class, params=params2)

        self.multioutput_models={}
        self.multioutput_models['YELP']=MultiHeadClassifierModel(model_file='enet3_multihead_model', model_class=enet, output_params=[('cuisine',params3),('labels',params1)])

        
    def recognize(self, img, print_time=True):
        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        model2descr={modelname:classifier.recognize(img_rgb, print_time) for modelname,classifier in self.models.items()}
        for modelname,classifier in self.multioutput_models.items():
            out2descr=classifier.recognize(img_rgb, print_time)
            for outname in out2descr:
                model2descr[modelname+' '+outname]=out2descr[outname]
        return model2descr
    
    def get_cuisine_prediction(self,restaurants_model2descr):
        return restaurants_model2descr['YELP cuisine'][1][0]
    

def recognize_restaurants_in_album(restaurantProcessing,album_dir,scene2files):
    filtered_files = set()
    if scene2files is not None:
        for filtered_scene in RESTAURANT_SCENES:
            if filtered_scene in scene2files:
                filtered_files|=scene2files[filtered_scene]

    model_file2scores={}
    for modelname,classifier in ChainMap(restaurantProcessing.models,restaurantProcessing.multioutput_models).items():
        model_file=classifier.model_file
        scores_file=os.path.join(album_dir,'yelp_'+model_file+'.dump')
        start_time=time.time()
        file2scores_orig={}
        if os.path.exists(scores_file):
            with open(scores_file, "rb") as f:
                file2scores_orig=pickle.load(f)                
        print(scores_file,len(filtered_files),len(file2scores_orig))

        #process images
        file2scores={}
        new_file_found=False
        for img_index,image_path in enumerate(filtered_files):
            if image_path in file2scores_orig:
                output_scores=file2scores_orig[image_path]
            else:
                new_file_found=True
                img=cv2.imread(os.path.join(album_dir,image_path))
                image_np = cv2.cvtColor(img,cv2.COLOR_BGR2RGB)
                output_scores=classifier.predict(image_np)

            file2scores[image_path]=output_scores
            print ('Processed photos: %d/%d\r'%(img_index+1,len(filtered_files)),end='')
            sys.stdout.flush()
            
        elapsed_time = time.time() - start_time
        if new_file_found:
            with open(scores_file, "wb") as f:
                pickle.dump(file2scores,f)
        print('restaurants processing ',model_file,' elapsed=',elapsed_time,' for ',len(filtered_files),' files')
        model_file2scores[modelname]=file2scores
        
    return model_file2scores

def get_restaurants2files(restaurantProcessing, model_file2scores):
    restaurants_model2files={}
    for modelname,file2scores in model_file2scores.items():
        files = list(file2scores.keys()) 
        scores_list=list(file2scores.values())
        if len(scores_list)>0:
            if modelname in restaurantProcessing.models:
                classifier=restaurantProcessing.models[modelname]
                try:                
                    album_preds=np.array(scores_list)#[:,:-1]
                    album_preds_thresholded=album_preds*(album_preds>classifier.params.threshold)
                    idxs, cl_idxs = np.nonzero(album_preds_thresholded)
                    unique_cl_idxs=np.unique(cl_idxs)
                    restaurants2files = {classifier.params.labels[j]:set([files[i] for i in idxs[cl_idxs==j]]) for j in unique_cl_idxs}
                    if len(restaurants2files)>0:
                        restaurants_model2files[modelname]=restaurants2files
                except Exception as ex:
                    print(ex,'restaurant album preds:',album_preds.shape,(album_preds>classifier.params.threshold).shape)
                    restaurants2files=None
            else:
                classifier=restaurantProcessing.multioutput_models[modelname]
                try:
                    for i,output_param in enumerate(classifier.output_params):
                        album_preds=np.array([scores[i] for scores in scores_list])#[:,:-1]
                        album_preds_thresholded=album_preds*(album_preds>output_param[1].threshold)
                        idxs, cl_idxs = np.nonzero(album_preds_thresholded)
                        unique_cl_idxs=np.unique(cl_idxs)
                        restaurants2files = {output_param[1].labels[j]:set([files[i] for i in idxs[cl_idxs==j]]) for j in unique_cl_idxs}
                        if len(restaurants2files)>0:
                            restaurants_model2files[modelname+' '+output_param[0]]=restaurants2files
                except Exception as ex:
                    print(ex,'restaurant album preds:',album_preds.shape,(album_preds>classifier.params.threshold).shape)
                    restaurants2files=None

    return restaurants_model2files

def process_all_images(filenames):
    restaurantProcessing=RestaurantProcessing()
    for filename in filenames:
        img = cv2.imread(filename)
        model2descr=restaurantProcessing.recognize(img)
        descr='_'.join([str(v[0]) for v in model2descr.values()])
        print(filename,model2descr, descr)
        draw = cv2.resize(img, (400,400))
        cv2.imshow(descr, draw)

        cv2.waitKey(-1)
        cv2.destroyAllWindows()  

if __name__ == '__main__':
    if len(sys.argv)>=1:
        process_all_images(sys.argv[1:])