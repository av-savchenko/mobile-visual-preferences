from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import sys
import os
import argparse
import tensorflow as tf
import numpy as np
import cv2
import time
import pickle
import pandas as pd
import shutil
import math
from collections import OrderedDict

import matplotlib.pyplot as plt

from tensorflow.keras.applications import mobilenet,mobilenet_v2,densenet,inception_v3
from tensorflow.keras.applications.imagenet_utils import preprocess_input as _preprocess_input
import efficientnet.tfkeras as enet

dir_path = os.path.dirname(os.path.abspath(__file__))
new_sys_dir = os.path.join(dir_path,'..')
if not new_sys_dir in sys.path:
    sys.path.append(new_sys_dir)

from facial_analysis.facial_analysis import FacialImageProcessing
from facial_analysis.process_photos import is_image,is_video

def read_proper_categories(filename):
    #highlevel_categories=set()
    highlevel_category_dict={}
    #category2indices={}
    #index2category={}
    for line in open(filename):
        s=line.rstrip('\n').split('#')[0]
        highlevel_category,category,indices_str=s.split('=')
        #highlevel_categories.add(highlevel_category)
        highlevel_category_dict[category]=highlevel_category
        #if category not in category2indices:
        #    category2indices[category]=list()
        #indices=[int(ind) for ind in indices_str.split(',')]
        #category2indices[category].extend(indices)
        #for ind in indices:
        #    index2category[ind]=category
    return highlevel_category_dict#, category2indices, index2category

def get_histogram(label2files, min_count):
    label2count=None
    if label2files is not None:
        label2count={label:len(files) for label,files in label2files.items() if len(files)>=min_count}
        label2count=OrderedDict(sorted(label2count.items(), key=lambda item: -item[1]))
    return label2count

def display_histograms(title2histograms,outfile=None):
    if len(title2histograms)>0:
        width = 0.35       # the width of the bars
        plt_w,plt_h=8,4
        if len(title2histograms)>1:
            num_rows=math.ceil(len(title2histograms)/2)
            fig, axes = plt.subplots(num_rows,2, figsize=(plt_w,plt_h*num_rows), squeeze=False)
        else:
            fig, axes = plt.subplots(1,1, figsize=(plt_w,plt_h), squeeze=False)
        colors=['r','g','b','c','m']
        
        for i,title in enumerate(title2histograms):
            ax=axes[i//2, 0 if i%2==0 else 1]
            #plot histo
            histogram=title2histograms[title]
            print(title)
            for label in histogram:
                print('%s:[%d]'%(label,histogram[label]))

            ind = np.arange(len(histogram))
            bars=ax.bar(ind,histogram.values(),width,color=colors[i%len(colors)])
            ax.set_ylabel('Count')
            ax.set_title(title)
            ax.set_xticks(ind)
            ax.set_xticklabels(histogram.keys())
            ax.set_ylim([0, 1 if len(histogram)==0 else max(histogram.values())])
            for label in ax.get_xticklabels():
                label.set_ha("right")
                label.set_rotation(30)

        #fig.subplots_adjust(hspace=0.4,wspace=0.4)
        plt.tight_layout()
        if outfile is None:
            plt.show()
        else:
            plt.savefig(outfile)
    else:
        print('Nothing found for high-level categories!')

        
class SceneProcessing:
    def __init__(self, model_name,preprocessing_func, scene_threshold=0.5, event_threshold=0):
        src_file_path,_ = os.path.split(os.path.realpath(__file__))
        models_path=os.path.join(src_file_path,'..','models','scenes')
        model_path=os.path.join(models_path,model_name+'.pb')
        print(model_path)

        self.model_name=model_name
        self.all_labels = [line.rstrip('\n').split('=')[0] for line in open(os.path.join(src_file_path,'scenes_places.txt'))]
        self.event_all_labels = [line.rstrip('\n').split('=')[0] for line in open(os.path.join(src_file_path,'events.txt'))]
        self.our_event_labels=list(set(self.event_all_labels))
        self.event_to_class={i:label for i,label in enumerate(self.our_event_labels)}

        self.scene_threshold=scene_threshold
        self.event_threshold=event_threshold        
        
        self.our_labels=list(set(self.all_labels))
        self.label_to_class={i:label for i,label in enumerate(self.our_labels)}
        
        self.highlevelCategories = read_proper_categories(os.path.join(src_file_path,'proper_scenes.txt'))
        print ([label for label in self.highlevelCategories if label not in self.our_labels])
            
        highlevelEventCategories = read_proper_categories(os.path.join(src_file_path,'proper_events.txt'))
        print ([label for label in highlevelEventCategories if label not in self.our_event_labels])
        
        self.highlevelCategories.update(highlevelEventCategories)
        
        
        scenes_graph=FacialImageProcessing.load_graph(model_path)
        self.sess=tf.compat.v1.Session(graph=scenes_graph)#,config=tf.ConfigProto(device_count={'CPU':1,'GPU':0}))
        self.scenes_func=self.load_scenes(self.sess,scenes_graph,preprocessing_func)

    def close(self):
        self.sess.close()
    
    def load_scenes(self,sess,graph,preprocessing_func):
        output=graph.get_tensor_by_name('dense_1/Softmax:0')
        print(output)

        output1=output
        output2=graph.get_tensor_by_name('event_fc/BiasAdd:0')
        print(output2)
        output=[output1,output2]

        input=graph.get_tensor_by_name('input_1:0')
        _,w,h,_=input.shape
        print(input,w,h)
        self.width,self.height=w,h
        def scenes_func(img):
            resized_image = cv2.resize(img, (w,h))
            x=resized_image.astype(np.float32)
            x = np.expand_dims(x, axis=0)
            x = preprocessing_func(x)

            preds = sess.run(output, feed_dict={input: x})
            #print(indices[:3],preds[indices[:3]],[label_to_class[i] for i in indices[:3]])
            return preds
        return scenes_func
    
    def get_label_to_class_and_threshold(self, isScene):
        if isScene:
            return self.label_to_class,self.scene_threshold
        else:
            return self.event_to_class,self.event_threshold
    
    def get_scene_description(self,preds, isScene=True):
        indices=preds.argsort()[::-1]
        label_to_class,_ = self.get_label_to_class_and_threshold(isScene)
        label, score = label_to_class[indices[0]],preds[indices[0]]
        return '%s (%.2f)'%(label, score)

    def get_scene_event_description(self,preds):
        scene_result=self.get_scene_description(self.get_our_preds(preds[0],True)[0],True)
        event_result=self.get_scene_description(self.get_our_preds(preds[1],False)[0],False)
        return scene_result+' '+event_result

    def recognize_scene_event(self, img, print_time=True):
        t = time.time()
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        preds = self.scenes_func(img)
        elapsed = time.time() - t
        if print_time:
            print('Scene recognition elapsed', elapsed)
        return self.get_scene_event_description(preds)

    def get_our_preds(self,all_predictions, isScene=True):
        if isScene:
            all_labels=self.all_labels
            our_labels=self.our_labels
        else:
            all_labels=self.event_all_labels
            our_labels=self.our_event_labels
        all_our_predictions=[]
        for predictions in all_predictions:
            our_preds={}
            for i,pred in enumerate(predictions):
                our_label=all_labels[i]
                if our_label in our_preds:
                    our_preds[our_label]+=pred
                else:
                    our_preds[our_label]=pred
            our_preds_values=[our_preds[our_label] for our_label in our_labels]
            all_our_predictions.append(our_preds_values)
            
        return np.array(all_our_predictions)

def enet_preprocess_input(x, **kwargs):
    #kwargs = {k: v for k, v in kwargs.items() if k in ['backend', 'layers', 'models', 'utils']}
    #return _preprocess_input(x, mode='torch', **kwargs)
    x /= 255.
    #mean = [0.5, 0.5, 0.5]
    #std = [1, 1, 1]
    mean = [0.485, 0.456, 0.406]
    std = [0.229, 0.224, 0.225]
    x[..., 0] -= mean[0]
    x[..., 1] -= mean[1]
    x[..., 2] -= mean[2]
    x[..., 0] /= std[0]
    x[..., 1] /= std[1]
    x[..., 2] /= std[2]
    return x

def get_scene_recognizer(mostAccurate=False):
    ind = 1 if mostAccurate else 0
    MODEL_NAMES=['places_event_enet0_augm_ft_sgd_model','places_event_enet3_augm_ft_sgd_model']
    PREPROCESSING_FUNCTIONS=[enet_preprocess_input,enet_preprocess_input]
    return SceneProcessing(MODEL_NAMES[ind],PREPROCESSING_FUNCTIONS[ind],0.15)
    
def process_video(sceneProcessing, album_dir, video_file, show_video=False):
    video_filepath=os.path.join(album_dir, video_file)
    
    counter=0
    VIDEO_FRAMES_RATE=5
    video = cv2.VideoCapture(video_filepath)
    
    video_scenes_preds,video_events_preds=[],[]
    
    while video.isOpened():
        #_, draw = video.read()
        if video.grab()==0:
            break
        counter+=1
        if counter%VIDEO_FRAMES_RATE!=0:
            continue
        _,draw=video.retrieve()
        height, width, channels = draw.shape
        draw=cv2.resize(draw, (sceneProcessing.width,sceneProcessing.height))
        
        image_np = cv2.cvtColor(draw,cv2.COLOR_BGR2RGB)
        output_scene=sceneProcessing.scenes_func(image_np)
        
        if show_video:
            scene=sceneProcessing.get_scene_event_description(output_scene)
            cv2.putText(draw,scene, (10, 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0,0,0))
            #print(scene)
            cv2.imshow(video_file, draw)

        video_scenes_preds.append(output_scene[0][0])
        video_events_preds.append(output_scene[1][0])
        if cv2.waitKey(1) == 27: 
            break  # esc to quit
    
    if show_video:
        cv2.destroyAllWindows()
    
    video_avg_scenes_preds=np.array(video_scenes_preds).mean(axis=0)
    video_avg_events_preds=np.array(video_events_preds).mean(axis=0)
    return video_avg_scenes_preds,video_avg_events_preds


def recognize_scenes_in_album(sceneProcessing,album_dir,image_files, video_files):
    scenes_file=os.path.join(album_dir,'scenes_'+sceneProcessing.model_name+'.dump')
    start_time=time.time()
    file2scene_orig={}
    if os.path.exists(scenes_file):
        with open(scenes_file, "rb") as f:
            file2scene_orig=pickle.load(f)
    print(scenes_file,len(image_files),len(file2scene_orig))
    #process images
    file2scene={}
    new_file_found=False
    for img_index,image_path in enumerate(image_files):
        if image_path in file2scene_orig:
            output_scene_event=file2scene_orig[image_path]
        else:
            new_file_found=True
            img=cv2.imread(os.path.join(album_dir,image_path))
            image_np = cv2.cvtColor(img,cv2.COLOR_BGR2RGB)
            output_scene=sceneProcessing.scenes_func(image_np)

            output_scene_event=(output_scene[0][0],output_scene[1][0])

        file2scene[image_path]=output_scene_event
        print ('Processed photos: %d/%d\r'%(img_index+1,len(image_files)),end='')
        sys.stdout.flush()
    
    print()            
    
    for i,video_path in enumerate(video_files):
        if video_path in file2scene_orig:
            output_scene_event=file2scene_orig[video_path]
        else:
            output_scene_event=process_video(sceneProcessing, album_dir, video_path, show_video=False)
        file2scene[video_path]=output_scene_event
    
    elapsed_time = time.time() - start_time
    print('scene processing ',sceneProcessing.model_name,' elapsed=',elapsed_time,' for ',len(image_files),' files and ',len(video_files),' videos')
    if new_file_found:
        with open(scenes_file, "wb") as f:
            pickle.dump(file2scene,f)
        
    return file2scene

def process_all_images(filenames):
    sceneProcessing=get_scene_recognizer(mostAccurate=False)
    for filename in filenames:
        draw = cv2.imread(filename)
        height, width, channels = draw.shape
        draw=cv2.resize(draw, (sceneProcessing.width,sceneProcessing.height))
        descr=sceneProcessing.recognize_scene_event(draw)
        print(filename,descr)
        cv2.imshow(descr, draw)

        cv2.waitKey(-1)
        cv2.destroyAllWindows()
    
    sceneProcessing.close()

def process_album(album_dir):
    image_files=[f for f in next(os.walk(album_dir))[2] if is_image(f)]
    #process_all_images(image_files)
    mostAccurate=True #False #
    sceneProcessing=get_scene_recognizer(mostAccurate=mostAccurate)
    file2scene=recognize_scenes_in_album(sceneProcessing,album_dir,image_files, [])
    scene2files=get_scene2files(sceneProcessing, file2scene, album_dir, save_events=True)
    scene2count=get_histogram(scene2files, min_scenes_count=2)
    if scene2count is not None:
        if True:
            title2scenes={'Scenes':scene2count}
        else:
            title2scenes=get_highlevel_scenes_histogram(sceneProcessing.highlevelCategories, scene2count)
        
        display_histograms(title2scenes)

    sceneProcessing.close()    
  
def combine_scenes_per_day(album_dir,file2scene, sceneProcessing, album_preds, isScene=True):
    day2preds={}
    label_to_class,threshold = sceneProcessing.get_label_to_class_and_threshold(isScene)
    for album_pred,f in zip(album_preds,file2scene):
        tm=time.gmtime(os.path.getmtime(os.path.join(album_dir,f)))
        day='%d_%02d_%02d'%(tm.tm_year,tm.tm_mon,tm.tm_mday)
        if day not in day2preds:
            day2preds[day]=[]
        day2preds[day].append((f,album_pred))
    
    avg_photo_per_day=np.mean([len(day2pred) for day2pred in day2preds.values()])
    min_no_photos_per_day=max(3,avg_photo_per_day*1.0)
    #print(avg_photo_per_day,min_no_photos_per_day)

    res_dir=os.path.join(album_dir,'events')
    if os.path.exists(res_dir):
        shutil.rmtree(res_dir,ignore_errors=True)
        time.sleep(1)
            
    event_ind=0
    for day,file_preds in sorted(day2preds.items(), key=lambda kv: -len(kv[1])):
        if len(file_preds)<=min_no_photos_per_day:
            break
        preds=np.array([file_pred[1] for file_pred in file_preds])
        if True:
            avg_preds=np.mean(preds,axis=0)
        else:
            avg_preds=np.median(preds,axis=0)
            avg_preds/=np.sum(avg_preds)
        avg_scene_indices=np.where(avg_preds>threshold)[0]
        avg_scene_indices=sorted(avg_scene_indices, key=lambda k: -avg_preds[k])
        if len(avg_scene_indices)>0:
            event_ind+=1
            avg_scenes=[label_to_class[scene_ind] for scene_ind in avg_scene_indices]
            #event_dir=os.path.join(res_dir,'%03d_%s_'%(event_ind,day)+'_'.join(avg_scenes))
            event_dir=os.path.join(res_dir,day+'_'+'_'.join(avg_scenes))
            os.makedirs(event_dir)
            for scene_ind,avg_scene in zip(avg_scene_indices,avg_scenes):
                best_file=file_preds[np.argmax(preds[:,scene_ind])][0]
                
                full_photo = cv2.imread(os.path.join(album_dir,best_file))
                r = 200.0 / full_photo.shape[1]
                dim = (200, int(full_photo.shape[0] * r))
                cv2.imwrite(os.path.join(event_dir,avg_scene+'_'+os.path.basename(best_file)),cv2.resize(full_photo, dim))

def get_scene2files(sceneProcessing, file2scene, album_dir, save_events):
    scene2files=None
    files = list(file2scene.keys())
    for isScene in [True,False]:
        scene_ind=0 if isScene else 1
        scene=[scene_event[scene_ind] for scene_event in file2scene.values()]
        album_preds=np.array(scene)#[:,:-1]
        if len(album_preds)>0:
            label_to_class, threshold = sceneProcessing.get_label_to_class_and_threshold(isScene)
            album_preds=sceneProcessing.get_our_preds(album_preds,isScene)
            album_preds_thresholded=album_preds*(album_preds>threshold)
            if save_events:
                combine_scenes_per_day(album_dir,file2scene, sceneProcessing, album_preds, isScene)
            
            idxs, cl_idxs = np.nonzero(album_preds_thresholded)
            unique_cl_idxs=np.unique(cl_idxs)
            scene2files_current = {label_to_class[j]:set([files[i] for i in idxs[cl_idxs==j]]) for j in unique_cl_idxs}

            if scene2files is None:
                scene2files=scene2files_current
            else:
                for label,file_list in scene2files_current.items():
                    if label in scene2files:
                        scene2files[label]|=file_list
                    else:
                        scene2files[label]=file_list
    return scene2files
    

def get_highlevel_scenes_histogram(highlevelCategories, scene2count):
    hl2scene2count={}
    if scene2count is not None:    
        topCategories=set(highlevelCategories.values())
        hl2scene2count={}
        for hl in topCategories:
            hl_scene2count=OrderedDict([(k,float(v)) for k, v in scene2count.items() if k in highlevelCategories and highlevelCategories[k]==hl])
            if len(hl_scene2count)>0:
                hl2scene2count[hl]=hl_scene2count
    return hl2scene2count


if __name__ == '__main__':
    if len(sys.argv)==2:
        sceneProcessing=get_scene_recognizer(mostAccurate=True)
        album_dir,video_path=os.path.split(sys.argv[1])
        process_video(sceneProcessing,album_dir,video_path, show_video=True)
        sceneProcessing.close()
    elif len(sys.argv)>2:
        process_all_images(sys.argv[1:])
    else:
        process_album('../downloaded_photos/sinikolenko/') #iphone_short
