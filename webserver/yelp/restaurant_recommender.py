from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import sys
import os
import argparse
import numpy as np
import pandas as pd

import cv2
import time
import pickle
import math

import matplotlib.pyplot as plt
try:
    from yelp.restaurant_analysis import RestaurantProcessing,RESTAURANT_CUISINES
except:
    from restaurant_analysis import RestaurantProcessing,RESTAURANT_CUISINES

NUM_MAX_PREDS=10

class RestaurantRecommender:
    def __init__(self,restaurants_info_file='restaurants.gz'):
        src_file_path,_ = os.path.split(os.path.realpath(__file__))
        restaurants_df = pd.read_csv(os.path.join(src_file_path,restaurants_info_file))
        #print(restaurants_df.head())
                    
        cuisine_labels=np.array(restaurants_df[RESTAURANT_CUISINES])
        #print(RESTAURANT_CUISINES)
        #print(cuisine_labels.shape,cuisine_labels[:5,:])
        #print(cuisine_labels.sum(axis=1),cuisine_labels.sum(axis=1, keepdims=True).shape)
        #cuisine_labels=cuisine_labels/cuisine_labels.sum(axis=1, keepdims=True)
        self.cuisine_labels=np.transpose(cuisine_labels)
        #print(self.cuisine_labels.shape,self.cuisine_labels[:,:5])

        restaurants_df=restaurants_df[['name','categories','city', 'latitude','longitude','stars']]
        newcol = restaurants_df.apply(lambda row: ','.join([our_category for our_category in RESTAURANT_CUISINES if our_category in row[1]]),axis=1)
        self.restaurants_info_df=restaurants_df.assign(categories=newcol)
        #print(len(self.restaurants_info_df),self.restaurants_info_df.head())

        #self.cities=self.restaurants_info_df.city.unique()
        cities=self.restaurants_info_df.groupby('city')['city'].count().sort_values(ascending=False)
        self.cities=cities[cities>100].index.tolist()
        if False:
            for c in self.cities:
                print('<option value="'+c+'">'+c+'</option>')
            sys.exit(0)

    def recommend(self, cuisine_prediction, city=None, num_recommendations=5, min_value_ratio=1.2):
        if cuisine_prediction is None or len(cuisine_prediction)==0:
            return None
        if len(cuisine_prediction.shape)>1:
            cuisine_prediction=cuisine_prediction.mean(axis=0)
        #print('orig:',cuisine_prediction)
        cuisine_prediction[cuisine_prediction<0.15]=0
        cuisine_prediction/=cuisine_prediction.sum()
        categories_sampled=list(np.random.choice(range(len(RESTAURANT_CUISINES)), num_recommendations, p=cuisine_prediction))
        categories_sampled.sort(key=lambda i: -cuisine_prediction[i])
        print(cuisine_prediction,categories_sampled, [(i,RESTAURANT_CUISINES[i]) for i in categories_sampled])
        
        result=[]
        for i in categories_sampled:
            indices=self.cuisine_labels[i,:]==1
            df=self.restaurants_info_df.iloc[indices]
            if city is not None and len(city)>0:
                df=df[df.city==city]        
            df = df.sample(frac=1)
            if len(df)>0:
                df=df.sort_values(by=['stars'], ascending=False)            
                for _, row in df.iterrows():
                    found=False
                    for r in result:
                        if row['name']==r[0]:
                            found=True
                            break
                    if not found:
                        res=row.values.tolist()
                        result.append(res)
                        break
        #result=set([tuple(x) for x in result])
        return result

    def recommend_simple(self, cuisine_prediction, city=None, num_recommendations=5, min_value_ratio=1.2):
        if cuisine_prediction is None or len(cuisine_prediction)==0:
            return None
        scores=-np.dot(np.log(cuisine_prediction),self.cuisine_labels)
        if len(scores.shape)>1:
            scores=scores.sum(axis=0)
        #print(scores,scores.min()*min_value_ratio,scores.min())
        indices=np.nonzero(scores<=scores.min()*min_value_ratio)[0]
        df=self.restaurants_info_df.iloc[indices]
        if city is not None and len(city)>0:
            df=df[df.city==city]        
        df = df.sample(frac=1)
        df=df.sort_values(by=['stars'], ascending=False)
        res=df.iloc[:num_recommendations].values.tolist()
        return res

    def recommend_for_album(self, restaurants_file2scores, city=None, num_recommendations=5):
        file2scores=restaurants_file2scores['YELP']
        scores_list=list(file2scores.values())
        cuisine_prediction=np.array([scores[0] for scores in scores_list])
        return self.recommend(cuisine_prediction, city, num_recommendations)

    def recommend_for_album_histo(self, cuisine2count, city=None, num_recommendations=10):
        cuisine_prediction=np.array([cuisine2count[cuisine] if cuisine in cuisine2count else 0 for cuisine in RESTAURANT_CUISINES], dtype=np.float)
        #print(cuisine_prediction)
        return self.recommend(cuisine_prediction, city, num_recommendations)
        
    def get_cities():
        return self.cities

def process_all_images(filenames, city='Las Vegas'):
    restaurantRecommender=RestaurantRecommender()
    restaurantProcessing=RestaurantProcessing()
    for filename in filenames:
        img = cv2.imread(filename)
        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        preds=np.array(restaurantProcessing.multioutput_models['YELP'].predict(img_rgb)[0])
        #preds=preds.reshape((1,-1))
        #print(filename,preds)
        descr=restaurantRecommender.recommend(preds, city=city)
        print(filename,descr)

def process_album(filenames, city='Las Vegas'):
    restaurantRecommender=RestaurantRecommender()
    restaurantProcessing=RestaurantProcessing()
    all_preds=[]
    for filename in filenames:
        img = cv2.imread(filename)
        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        preds=np.array(restaurantProcessing.multioutput_models['YELP'].predict(img_rgb)[0])
        all_preds.append(preds)
    descr=restaurantRecommender.recommend(np.array(all_preds), city=city)

if __name__ == '__main__':
    if len(sys.argv)>=1:
        process_all_images(sys.argv[1:])
        #process_album(sys.argv[1:])