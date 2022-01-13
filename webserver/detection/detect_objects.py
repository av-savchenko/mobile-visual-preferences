from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import sys
import os
import time,datetime
import cv2
import shutil
import pickle
import math
from collections import OrderedDict

import numpy as np
import tensorflow as tf

from PIL import Image
from PIL import ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True

import matplotlib.pyplot as plt

if 'TF_MODELS_DIR' in os.environ:
    TF_MODELS_DIR=os.environ['TF_MODELS_DIR']
else:
    print('env var TF_MODELS_DIR is not set. Use default')
    TF_MODELS_DIR='D:/src_code/models/research'
print(TF_MODELS_DIR)
sys.path.append(TF_MODELS_DIR)
sys.path.append(os.path.join(TF_MODELS_DIR,'object_detection'))


dir_path = os.path.dirname(os.path.abspath(__file__))
new_sys_dir = os.path.join(dir_path,'..')
if not new_sys_dir in sys.path:
    sys.path.append(new_sys_dir)

from utils import ops as utils_ops
from utils import label_map_util
from utils import visualization_utils as vis_util


from facial_analysis.process_photos import is_image,is_video

from scenes.scene_analysis import read_proper_categories


SCORE_THRESHOLD=0.1
class ObjectDetector:
    def __init__(self,mostAccurate=False, scoreThreshold=0.3, models_path='/opt/lab/tf_models/downloaded_models/'):
        self.FILTERED_INDICES=set([16,52,70,149,178,215,222,225,229,242,254,293,309,334,392,435,463,503,504,569,574])
        
        src_file_path,_ = os.path.split(os.path.realpath(__file__))

        MODEL_NAMES=['ssd_mobilenet_v2_oid_v4_2018_12_12',
                 'ssd_resnet101_v1_fpn_shared_box_predictor_oid_512x512_sync_2019_01_20',
                 'faster_rcnn_inception_resnet_v2_atrous_oid_v4_2018_12_12']
        model_idx=2 if mostAccurate else 0
        self.scoreThreshold=scoreThreshold

        self.model_name = MODEL_NAMES[model_idx]
        PATH_TO_CKPT = os.path.join(models_path,self.model_name, 'frozen_inference_graph.pb')
        print(PATH_TO_CKPT)

        detection_graph = tf.Graph()
        with detection_graph.as_default():
          od_graph_def = tf.compat.v1.GraphDef()
          with tf.io.gfile.GFile(PATH_TO_CKPT, 'rb') as fid:
            serialized_graph = fid.read()
            od_graph_def.ParseFromString(serialized_graph)
            tf.import_graph_def(od_graph_def, name='')

        PATH_TO_LABELS = os.path.join(TF_MODELS_DIR,'object_detection', 'data', 'oid_v4_label_map.pbtxt')
        NUM_CLASSES = 601
            
        label_map = label_map_util.load_labelmap(PATH_TO_LABELS)
        categories = label_map_util.convert_label_map_to_categories(label_map, max_num_classes=NUM_CLASSES, use_display_name=True)
        self.category_index = label_map_util.create_category_index(categories)
        
        self.highlevelCategories = read_proper_categories(os.path.join(src_file_path,'proper_objects.txt'))
        #category_names=set([category['name'] for category in categories])
        #print ([label for label in self.highlevelCategories if label not in category_names])
        
        ops = detection_graph.get_operations()
        all_tensor_names = {output.name for op in ops for output in op.outputs}
        # for t in all_tensor_names:
        #     if 'box' in t:
        #         print(t)

        tensor_dict = {}
        for key in ['detection_boxes', 'detection_scores','detection_classes', 'SecondStageBoxPredictor/AvgPool']:#'FeatureExtractor/MobilenetV2/expanded_conv_15/depthwise_output'
            tensor_name = key + ':0'
            if tensor_name in all_tensor_names:
                tensor_dict[key] = detection_graph.get_tensor_by_name(tensor_name)
      
        image_tensor = detection_graph.get_tensor_by_name('image_tensor:0')
        self.sess=tf.compat.v1.Session(graph=detection_graph)
        #self.sess=tf.compat.v1.Session(graph=detection_graph, config = tf.ConfigProto(device_count={'CPU':1,'GPU':0}))
        
        def detect_fun(image):
            output_dict = self.sess.run(tensor_dict,feed_dict={image_tensor: np.expand_dims(image, 0)})
            return output_dict
        self.detection_func=detect_fun
    
    def detect_objects(self,img):
        output_dict=self.detection_func(img)
        # all outputs are float32 numpy arrays, so convert types as appropriate
        score_indices=output_dict['detection_scores'][0]>=self.scoreThreshold
        output_dict['detection_scores'] = output_dict['detection_scores'][0][score_indices]
        output_dict['detection_classes'] = output_dict[
            'detection_classes'][0].astype(np.uint32)[score_indices]
        output_dict['detection_boxes'] = output_dict['detection_boxes'][0][score_indices]

        if self.model_name == 'faster_rcnn_inception_resnet_v2_atrous_oid_v4_2018_12_12':
            output_dict['SecondStageBoxPredictor/AvgPool'] = output_dict['SecondStageBoxPredictor/AvgPool'][score_indices]

        # for ssd+mobilenet_v2
        #tmp = output_dict['FeatureExtractor/MobilenetV2/expanded_conv_15/depthwise_output'][0]
        #output_dict['ssd_mobilenetv2_feature'] = np.reshape(tmp, (tmp.shape[0] * tmp.shape[1], tmp.shape[2]))[score_indices]
        return output_dict

    def draw_detection_results(self,img,output_dict=None, image_np=None):
        if output_dict is None:
            if image_np is None:
                image_np = cv2.cvtColor(img,cv2.COLOR_BGR2RGB)
            start_time=time.time()
            output_dict=self.detect_objects(image_np)
            elapsed_time = time.time() - start_time
            print('elapsed=',elapsed_time)
            print(output_dict['detection_classes'],output_dict['detection_scores'], [self.category_index[obj]['name'] for obj in output_dict['detection_classes']])

        # Visualization of the results of a detection.
        if max(img.shape[0],img.shape[1])>800:
            r = 480.0 / img.shape[0]
            dim = (int(img.shape[1] * r),480)
            img=cv2.resize(img,dim)
        vis_util.visualize_boxes_and_labels_on_image_array(
            img,
            output_dict['detection_boxes'],
            output_dict['detection_classes'],
            output_dict['detection_scores'],
            self.category_index,
            instance_masks=None,
            use_normalized_coordinates=True,
            min_score_thresh=self.scoreThreshold,
            line_thickness=8)
        return img

        
    def close(self):
        if self.sess:
            self.sess.close()

def draw_objects_in_image_dir(object_detector,test_images_dir):
    #test_images_dir='D:/datasets/my_photos/iphone/clusters/public'
    test_image_paths = [ img_fpath for img_fpath in os.listdir(test_images_dir) if is_image(img_fpath)]
    #test_full_images_dir='D:/datasets/my_photos/iphone' #test_images_dir
    test_full_images_dir=test_images_dir
    for image_path in test_image_paths[:]: #[:20]
        img=cv2.imread(os.path.join(test_full_images_dir,image_path))
        #img=cv2.resize(img, (480, 480))
        img=object_detector.draw_detection_results(img)
        cv2.imshow('detection', img)
        cv2.waitKey(-1)

def get_image_bbox(img,bbox):
    img_h,img_w,_=img.shape
    x1=int(bbox[1]*img_w)
    y1=int(bbox[0]*img_h)
    x2=int(bbox[3]*img_w)
    y2=int(bbox[2]*img_h)
    #face_img=img[y1:y2,x1:x2,:]
    return [x1,y1,x2,y2]
    

def process_video(object_detector, album_dir, video_file, show_video=False,min_object_count=3):
    video_filepath=os.path.join(album_dir, video_file)
    
    counter=0
    VIDEO_FRAMES_RATE=5
    video = cv2.VideoCapture(video_filepath)
    
    video_objects=[]
    video_face_bboxes=[]
    objects_count={}

    objects_file=os.path.join(album_dir,'objects_'+object_detector.model_name+'_'+os.path.basename(video_file)+'.dump')
    file2detectorOutput={}
    if os.path.exists(objects_file):
        with open(objects_file, "rb") as f:
            file2detectorOutput=pickle.load(f)
    
    if show_video:
        start_time=time.time()
    while video.isOpened():
        #_, draw = video.read()
        if video.grab()==0:
            break
        counter+=1
        if counter%VIDEO_FRAMES_RATE!=0:
            continue
        _,draw=video.retrieve()
        height, width, channels = draw.shape
        if width>640 or height>480:
            draw=cv2.resize(draw, (min(width,640),min(height,480)))
        
        if counter in file2detectorOutput:
            output_dict=file2detectorOutput[counter]
        else:
            image_np = cv2.cvtColor(draw,cv2.COLOR_BGR2RGB)
            output_dict=object_detector.detect_objects(image_np)
            file2detectorOutput[counter]=output_dict
        
        no_objects=len(output_dict['detection_classes'])
        
        #VIDEO_FRAMES_RATE=3 if num_faces==0 else 1
        if show_video:
            draw=object_detector.draw_detection_results(draw,output_dict)
            cv2.imshow(video_filepath, draw)
        img_objects={}
        face_bboxes=[]
        for i in range(no_objects):
            class_ind=output_dict['detection_classes'][i]
            score=output_dict['detection_scores'][i]
            if object_detector.category_index[class_ind]['name'] in ['Human face','Human head']:
                face_bboxes.append(get_image_bbox(draw,output_dict['detection_boxes'][i]))
            elif (class_ind+1) not in object_detector.FILTERED_INDICES and (class_ind not in img_objects or img_objects[class_ind]<score):
                img_objects[class_ind]=score
        
        video_face_bboxes.append(face_bboxes)
        video_objects.append(img_objects)
        for class_ind in img_objects:
            if class_ind not in objects_count:
                objects_count[class_ind]=1
            else:
                objects_count[class_ind]+=1
        if cv2.waitKey(1) == 27: 
            break  # esc to quit
    
    if show_video:
        elapsed_time = time.time() - start_time
        cv2.destroyAllWindows()
        print('elapsed=',elapsed_time)
    
    with open(objects_file, "wb") as f:
        pickle.dump(file2detectorOutput,f)
    
    frequent_objects={obj:objects_count[obj] for obj in sorted(objects_count, key=objects_count.get, reverse=True) if objects_count[obj]>=min_object_count and obj not in FILTERED_OBJECTS}
    if show_video:
        print('frequent objects:')
        for obj in frequent_objects:
            print('%s:[%d]'%(object_detector.category_index[obj]['name'],frequent_objects[obj]))
    
    return frequent_objects,video_face_bboxes

    
def detect_objects_in_album(object_detector,album_dir,image_files, video_files):
    objects_file=os.path.join(album_dir,'objects_'+object_detector.model_name+'.dump')
    start_time=time.time()
    file2detectorOutput_orig,album_face_bboxes_orig={},{}
    if os.path.exists(objects_file):
        with open(objects_file, "rb") as f:
            file2detectorOutput_orig,album_face_bboxes_orig=pickle.load(f)
    
    file2detectorOutput={}
    file2objects={}
    album_face_bboxes={}
    new_files=0
    new_file_found=False
    #process images
    for img_index,image_path in enumerate(image_files):
        img=None
        if image_path in file2detectorOutput_orig:
            output_dict=file2detectorOutput_orig[image_path]
        else:
            img=cv2.imread(os.path.join(album_dir,image_path))
            if img is None:
                continue
            new_file_found=True
            image_np = cv2.cvtColor(img,cv2.COLOR_BGR2RGB)
            output_dict=object_detector.detect_objects(image_np)
            new_files+=1
        file2detectorOutput[image_path]=output_dict
        no_objects=len(output_dict['detection_classes'])
        #print('obtained %d objects while processing of %s'%(no_objects,image_path))
        img_objects={}
        face_bboxes=[]
        if image_path in album_face_bboxes_orig:
            face_bboxes=album_face_bboxes_orig[image_path]
        for i in range(no_objects):
            class_ind=output_dict['detection_classes'][i]
            score=output_dict['detection_scores'][i]
            if object_detector.category_index[class_ind]['name'] in ['Human face','Human head']:
                if image_path not in album_face_bboxes_orig:
                    new_file_found=True
                    if img is None:
                        img=cv2.imread(os.path.join(album_dir,image_path))
                    bbox=get_image_bbox(img,output_dict['detection_boxes'][i])
                    face_bboxes.append(bbox)
            elif (class_ind+1) not in object_detector.FILTERED_INDICES and (class_ind not in img_objects or img_objects[class_ind]<score):
                img_objects[class_ind]=score
        
        album_face_bboxes[image_path]=face_bboxes
        file2objects[image_path]=img_objects
        print ('Processed photos: %d/%d\r'%(img_index+1,len(image_files)),end='')
        sys.stdout.flush()
                
    elapsed_time = time.time() - start_time
    print('\nobject detection elapsed=',elapsed_time,' for ',len(image_files),' files, objects_file=',objects_file, 'new_files=',new_files)
    if new_file_found:
        with open(objects_file, "wb") as f:
            print(f)
            pickle.dump([file2detectorOutput,album_face_bboxes],f)
    if True:
        print('all objects:')
        for image_path in file2objects:
            img_objects=file2objects[image_path]
            if len(img_objects)>0:
                print('image %s object count %d'%(image_path,len(img_objects)))
            for class_ind in img_objects:
                print('%s:[%f]'%(object_detector.category_index[class_ind]['name'],img_objects[class_ind]))
    
    #process videos
    video_faces=[]
    start_time=time.time()
    for i,video_path in enumerate(video_files):
        video_frequent_objects,video_face_bboxes=process_video(object_detector,album_dir,video_path, show_video=False)
        video_faces.append(video_face_bboxes)
        file2objects[video_path]=video_frequent_objects
        if True:
            print('frequent objects for ',video_path)
            for obj in video_frequent_objects:
                print('%s:[%d]'%(object_detector.category_index[obj]['name'],video_frequent_objects[obj]))
    elapsed_time = time.time() - start_time
    print('video detection elapsed=',elapsed_time,' for ',len(video_files),' videos')
    return file2objects,album_face_bboxes,video_faces

    
def process_album(object_detector,album_dir,minNumberOfObjectsToDisplay=2):
    public_photo_dir=album_dir
    #public_photo_dir=os.path.join(album_dir,'clusters','public')
    image_files=[f for f in next(os.walk(public_photo_dir))[2] if is_image(f)]
    video_files=[f for f in next(os.walk(album_dir))[2] if is_video(f)]
    file2objects,_,_=detect_objects_in_album(object_detector,album_dir,image_files,video_files)
    
    objects_count={}
    for img_objects in file2objects.values():
        for category in img_objects:
            if category not in objects_count:
                objects_count[category]=1
            else:
                objects_count[category]+=1
    frequent_objects=OrderedDict([(object_detector.category_index[obj]['name'],objects_count[obj]) for obj in sorted(objects_count, key=objects_count.get, reverse=True) if objects_count[obj]>=minNumberOfObjectsToDisplay])
    
    print('frequent objects:')
    if True:
        title2objects={'Objects':frequent_objects}
    else:
        title2objects=get_highlevel_objects(object_detector.highlevelCategories,frequent_objects)
    
    display_objects(title2objects)
    
    return frequent_objects,title2objects


def get_highlevel_objects(highlevelCategories,frequent_objects):
    topCategories=set(highlevelCategories.values())
    hl2object2count={}
    for hl in topCategories:
        hl_object2count=OrderedDict([(k, float(v)) for k, v in frequent_objects.items() if k in highlevelCategories and highlevelCategories[k]==hl])
        if len(hl_object2count)>0:
            hl2object2count[hl]=hl_object2count

    hl2object2count=OrderedDict([(k, v) for k, v in sorted(hl2object2count.items(), key=lambda item: -len(item[1]))])
    return hl2object2count
    
def display_objects(title2objects, outfile=None):
    if len(title2objects)>0:
        width = 0.35       # the width of the bars
        plt_w,plt_h=4,4
        if len(title2objects)>1:
            num_rows=math.ceil(len(title2objects)/2)
            fig, axes = plt.subplots(num_rows,2, figsize=(plt_w*2,plt_h*num_rows), squeeze=False)
        else:
            fig, axes = plt.subplots(1,1, figsize=(plt_w,plt_h), squeeze=False)
        colors=['r','g','b','c','m']
        
        print('Objects:')
        for i,hl in enumerate(title2objects):
            #plot histo
            object2count=title2objects[hl]
            print(hl)
            for obj in object2count:
                print('%s:[%d]'%(obj,object2count[obj]))
            frequent_categories=object2count.keys()
            objects_histo=list(object2count.values())
            ind = np.arange(len(object2count))

            ax=axes[i//2, 0 if i%2==0 else 1]
            bars=ax.bar(ind,objects_histo,width,color=colors[i%len(colors)])
            ax.set_ylabel('Count')
            ax.set_title(hl)
            ax.set_xticks(ind)
            ax.set_xticklabels(frequent_categories)
            ax.set_ylim([0, 1 if len(objects_histo)==0 else objects_histo[0]])
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
        print('No objects found for high-level categories!')    
        
if __name__ == '__main__':
    #'D:/datasets/my_photos/for_detection', 'D:/src_code/Mask_RCNN/images'
    object_detector=ObjectDetector(mostAccurate=True)
    if len(sys.argv)>1:
        from matplotlib import pyplot as plt
        from utils import visualization_utils as vis_util

        if len(sys.argv)>2:
            process_video(object_detector,sys.argv[2], show_video=True)
        else:
            draw_objects_in_image_dir(object_detector,sys.argv[1])
    else:
        process_album(object_detector,'../downloaded_photos/sinikolenko/')
    object_detector.close()
