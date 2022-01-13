from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import sys
import os
import time,datetime
import cv2
import shutil
import pickle
import numpy as np
from numpy import linalg as LA

import matplotlib.pyplot as plt

from configparser import ConfigParser

dir_path = os.path.dirname(os.path.abspath(__file__))
new_sys_dir = os.path.join(dir_path,'..')
if not new_sys_dir in sys.path:
    sys.path.append(new_sys_dir)

try:
    from facial_analysis.facial_analysis import FacialImageProcessing,is_image,is_video,get_image_file_info
    from facial_analysis.facial_clustering import get_facial_clusters
except:
    from facial_analysis import FacialImageProcessing,is_image,is_video,get_image_file_info
    from facial_clustering import get_facial_clusters
    
#config values
minDaysDifferenceBetweenPhotoMDates=2
minNoFrames=10
distanceThreshold=0.82
minFaceWidthPercent=0.05
minNoPhotos=2 #used in private photo selection
minNumberOfPhotosInDisplayedCluster=minNoPhotos #used in displayed clusters
processVideoFiles=1#process video files?
    
def init_configs(default_config):
    global minDaysDifferenceBetweenPhotoMDates,minNumberOfPhotosInDisplayedCluster,minNoFrames,distanceThreshold,minFaceWidthPercent,processVideoFiles
    minDaysDifferenceBetweenPhotoMDates=int(default_config['MinDaysDifferenceBetweenPhotoMDates'])
    minNumberOfPhotosInDisplayedCluster=int(default_config['MinNumberOfPhotosInDisplayedCluster'])
    minNoFrames=int(default_config['MinNoFrames'])
    distanceThreshold=float(default_config['DistanceThreshold'])
    minFaceWidthPercent=float(default_config['MinFaceWidthPercent'])/100
    processVideoFiles=int(default_config['ProcessVideoFiles'])
    
    print('minDaysDifferenceBetweenPhotoMDates:',minDaysDifferenceBetweenPhotoMDates,' minNoPhotos:',minNumberOfPhotosInDisplayedCluster,'minNoFrames:',minNoFrames,' distanceThreshold:',distanceThreshold,' minFaceWidthPercent:',minFaceWidthPercent,' processVideoFiles:',processVideoFiles)
    
def load_configs(config_file):
    config = ConfigParser()
    config.read(config_file)
    default_config=config['DEFAULT']
    init_configs(default_config)
    return default_config

img_size=224
def process_image(imgProcessing,img,face_bboxes=None):
    height, width, channels = img.shape
    bounding_boxes, _,ages,genders,ethnicities,facial_features=imgProcessing.process_image(img,face_bboxes)
    facial_images=[]
    has_center_face=False
    for bb in bounding_boxes:
        x1,y1,x2,y2=bb[0:4]
        face_img=cv2.resize(img[y1:y2,x1:x2,:],(img_size,img_size))
        facial_images.append(face_img)
        #dx=1.5*(x2-x1)
        if (x2-x1)/width>=minFaceWidthPercent: #x1-dx<=width/2<=x2+dx:
            has_center_face=True
    return facial_images,ages,genders,ethnicities,facial_features, has_center_face

def perform_clustering(mdates,all_indices,all_features,all_born_years,no_images_in_cluster, checkDates=True):
    def feature_distance(i,j):
        dist=np.sqrt(np.sum((all_features[i]-all_features[j])**2))
        max_year=datetime.datetime.now().year#max(mdates[all_indices[i]].tm_year,mdates[all_indices[j]].tm_year)
        cur_age_i,cur_age_j=max_year-all_born_years[i],max_year-all_born_years[j]
        if cur_age_i<7 or cur_age_j<7:
            age_dist=(cur_age_i-cur_age_j)**2/(cur_age_i+cur_age_j)
        else:
            age_dist=0
        return [dist,age_dist*0.2]
        
    num_faces=len(all_indices)
    if num_faces<no_images_in_cluster:
        return []

    t=time.time()
    pair_dist=np.array([[feature_distance(i,j) for j in range(num_faces)] for i in range(num_faces)])
    dist_matrix=np.clip(np.sum(pair_dist,axis=2),a_min=0,a_max=None)
    clusters=get_facial_clusters(dist_matrix,distanceThreshold,all_indices,no_images_in_cluster)
    elapsed = time.time() - t
    #print('clustering elapsed=%f'%(elapsed)) 
    
    #print('clusters',clusters)
    
    def is_good_cluster(cluster):
        res=len(cluster)>=no_images_in_cluster
        if res and checkDates:
            cluster_mdates=[mdates[all_indices[i]] for i in cluster]
            max_date,min_date=max(cluster_mdates),min(cluster_mdates)
            diff_in_days=(datetime.datetime.fromtimestamp(time.mktime(max_date))-datetime.datetime.fromtimestamp(time.mktime(min_date))).days
            res=diff_in_days>=minDaysDifferenceBetweenPhotoMDates
        return res
        
    filtered_clusters=[cluster for cluster in clusters if is_good_cluster(cluster)]
    #print('clustering ',len(clusters), 'new clusters:',len(filtered_clusters))

    return filtered_clusters

    
def process_video(imgProcessing,album_dir,video_file,mdate,video_bboxes=None):
    video_year=mdate.tm_year+(mdate.tm_mon-1)/12
    mdates=[]

    video_filepath=os.path.join(album_dir,video_file)
    counter=0
    VIDEO_FRAMES_RATE=5

    if video_bboxes is None:
        features_file=os.path.join(album_dir,'features_'+video_file+'.dump')
        print('video_bboxes is None')
    else:
        features_file=os.path.join(album_dir,'features_detector_'+video_file+'.dump')
        print('video_bboxes:',features_file)
        
    t=time.time()
    if os.path.exists(features_file):
        with open(features_file, "rb") as f:
            all_facial_images=pickle.load(f)
            all_born_years=pickle.load(f)
            all_genders=pickle.load(f)
            all_ethnicities=pickle.load(f)
            all_features=pickle.load(f)
            all_normed_features=pickle.load(f)
            all_indices=pickle.load(f)
            
            mdates=[] if len(all_indices)==0 else [mdate]*(all_indices[-1]+1)
    else:
        video = cv2.VideoCapture(video_filepath)
        all_facial_images,all_born_years, all_genders,all_ethnicities,all_features,all_normed_features,all_indices=[],[],[],[],[],[],[]
        
        frame_count=0
        while video.isOpened():
            if video.grab()==0:
                break
            counter+=1
            if counter%VIDEO_FRAMES_RATE!=0:
                continue
            _,draw=video.retrieve()
            height, width, channels = draw.shape
            #if width>640 or height>480:
            #    draw=cv2.resize(draw, (min(width,640),min(height,480)))
            facial_images,ages,genders,ethnicities,facial_features,has_center_face=process_image(imgProcessing,draw, None if video_bboxes is None else video_bboxes[frame_count])
            all_facial_images.extend(facial_images)
            all_genders.extend(genders)
            all_ethnicities.extend(ethnicities)
            all_features.extend(facial_features)
            for features in facial_features:
                all_normed_features.append(features/np.sqrt(np.sum(features**2)))
            all_indices.extend([frame_count]*len(ages))
            mdates.append(mdate)
            all_born_years.extend([(video_year-(age-0.5)) for age in ages])
            frame_count+=1
            #VIDEO_FRAMES_RATE=5 if len(ages)==0 else 3
        
        with open(features_file, "wb") as f:
            pickle.dump(all_facial_images,f)
            pickle.dump(all_born_years, f)
            pickle.dump(all_genders,f)
            pickle.dump(all_ethnicities,f)
            pickle.dump(all_features,f)
            pickle.dump(all_normed_features, f)
            pickle.dump(all_indices,f)
        print('features dumped into',features_file) 
    elapsed = time.time() - t

    all_born_years=np.array(all_born_years)
    all_genders=np.array(all_genders)
    all_ethnicities=np.array(all_ethnicities)
    all_features=np.array(all_features)
    all_normed_features=np.array(all_normed_features)
    
    print('\nfacial video %s processing elapsed=%f'%(os.path.basename(video_filepath),elapsed))            
    
    filtered_clusters=perform_clustering(mdates,all_indices,all_normed_features,all_born_years,minNoFrames, checkDates=False)
    
    if False:
        no_clusters=min(10,len(filtered_clusters))
        plt_ind=1
        for i in range(no_clusters):
            l=len(filtered_clusters[i])
            step=l//minNoFrames
            for j in range(0,step*minNoFrames,step):
                plt.subplot(no_clusters,minNoFrames,plt_ind)
                plt.imshow(cv2.cvtColor(all_facial_images[filtered_clusters[i][j]],cv2.COLOR_BGR2RGB))
                plt.axis('off')
                plt_ind+=1

        plt.show()
    
    cluster_facial_images,cluster_ages,cluster_genders,cluster_ethnicities,cluster_facial_features=[],[],[],[],[]
    for i,cluster in enumerate(filtered_clusters):
        avg_gender_preds=np.median(all_genders[cluster])
        avg_ethnicities_preds=np.mean(all_ethnicities[cluster],axis=0)
        avg_year=np.median(all_born_years[cluster])
        print('cluster ',i,avg_gender_preds,avg_year,avg_ethnicities_preds)
        cluster_facial_images.append(all_facial_images[cluster[0]])
        cluster_genders.append([avg_gender_preds])
        cluster_ethnicities.append(avg_ethnicities_preds)
        cluster_ages.append(int(video_year-(avg_year-0.5)))
        cluster_facial_features.append(np.mean(all_features[cluster],axis=0))
    
    video_has_faces=len(filtered_clusters)>0
    return cluster_facial_images,cluster_ages,cluster_genders,cluster_ethnicities,cluster_facial_features,video_has_faces

    
#Dempster-Shafer implementation
def calculate_proximity(dt, predictions, num_of_classes=100):
    prox_classes = []
    for i in range(0,num_of_classes):
        class_preds = predictions
        class_dt = dt[i]
        norm_vect = np.power((1+LA.norm(np.subtract(class_dt, class_preds))), -1)
        #norm_vect = np.power((1 + np.sum(abs((np.subtract(class_dt, class_preds))), axis=0)), -1)
        prox_classes.append(norm_vect)
    norm_prox_classes = prox_classes/sum(prox_classes)
    return norm_prox_classes


def compute_belief_degrees(proximities, num_of_classes):
    belief_degrees = []
    current_classifier_prox = proximities
    for j in range(0, num_of_classes):
        class_mult = [(1-current_classifier_prox[k]) for k in range(0, num_of_classes) if k != j]
        num = (current_classifier_prox[j] * np.prod(class_mult))
        denom = (1 - current_classifier_prox[j])*(1-np.prod(class_mult))
        cl_ev = num / denom
        belief_degrees.append(cl_ev)
        print(np.sum(belief_degrees))
    return belief_degrees

def compute_b(proximities, num_of_classes):
    belief_degrees = []
    for j in range(0, num_of_classes):
        class_mult = [(1-proximities[k]) for k in range(0, num_of_classes) if k != j]
        #num = (proximities[j] * np.prod(class_mult))
        #denom = 1 - proximities[j]*(1-np.prod(class_mult))
        #cl_ev = (num / denom)
        num = np.log(proximities[j]) + np.sum(np.log(class_mult))
        denom = np.log(1-proximities[j]*(1-np.prod(class_mult)))
        cl_ev = num-denom
        belief_degrees.append(cl_ev)
    return belief_degrees

def final_decision(log_belief_degrees):
    #belief_degrees = np.log(np.asarray(belief_degrees))
   # belief_degrees = np.exp(np.log(np.asarray(belief_degrees)))
    #m = np.prod(belief_degrees, axis=0, dtype=np.float32)
    log_m = np.sum(log_belief_degrees, axis=0)
    #m = np.exp(log_m)
    m=log_m
    #print(m)
    index = m.argsort()[::-1][:1]
    return index[0]

def dempster_shafer_gender(male_probabs):
    dt=[[0.875,0.125],[0.353,0.647]]
    beliefs=[]
    for male_probab in male_probabs:
        gender_preds = [[male_probab[0],1-male_probab[0]]]
        gender_proximities = calculate_proximity(dt, gender_preds, 2)
        b = compute_b(gender_proximities,2)
        beliefs.append(b)
    ds_gender = final_decision(beliefs)
    return ds_gender

    
    
def process_album(imgProcessing,album_dir, save_facial_clusters=True, save_public_photos=False, no_clusters_to_show=10,album_face_bboxes=None,video_bboxes=None):
    if album_face_bboxes is None:
        features_file=os.path.join(album_dir,'features.dump')
    else:
        features_file=os.path.join(album_dir,'features_detector.dump')
    t=time.time()
    files,mdates,all_facial_images,all_born_years, all_genders,all_ethnicities,all_features,all_indices,private_photo_indices=[],[],[],[],[],[],[],[],[]
    camera_models,camera_info=[],[]
    if os.path.exists(features_file):
        with open(features_file, "rb") as f:
            files=pickle.load(f)
            mdates=pickle.load(f)
            all_facial_images=pickle.load(f)
            all_born_years=pickle.load(f)
            all_genders=pickle.load(f)
            all_ethnicities=pickle.load(f)
            all_features=pickle.load(f)
            all_indices=pickle.load(f)
            private_photo_indices=pickle.load(f)
            camera_models=pickle.load(f)
            camera_info=pickle.load(f)

    print(features_file,album_dir,len(files))
    #process static images
    new_files=[f for f in next(os.walk(album_dir))[2] if is_image(f) and f not in files]
    #files=files[:20]
    new_mdates=[time.gmtime(os.path.getmtime(os.path.join(album_dir,f))) for f in new_files]
    files.extend(new_files)
    mdates.extend(new_mdates)
    for i,fpath in enumerate(new_files):
        file_path=os.path.join(album_dir,fpath)
        full_photo = cv2.imread(file_path)
        facial_images,ages,genders,ethnicities,facial_features,has_center_face=process_image(imgProcessing,full_photo,None if album_face_bboxes is None else album_face_bboxes[fpath])
        if len(facial_images)==0:
            full_photo_t=cv2.transpose(full_photo)
            rotate90=cv2.flip(full_photo_t,1)
            facial_images,ages,genders,ethnicities,facial_features,has_center_face=process_image(imgProcessing,rotate90)
            if len(facial_images)==0:
                rotate270=cv2.flip(full_photo_t,0)
                facial_images,ages,genders,ethnicities,facial_features,has_center_face=process_image(imgProcessing,rotate270)
        if has_center_face:
            private_photo_indices.append(i)
        all_facial_images.extend(facial_images)
        all_genders.extend(genders)
        all_ethnicities.extend(ethnicities)
        for features in facial_features:
            features=features/np.sqrt(np.sum(features**2))
            all_features.append(features)
        all_indices.extend([i]*len(ages))

        photo_year=new_mdates[i].tm_year+(new_mdates[i].tm_mon-1)/12
        all_born_years.extend([(photo_year-(age-0.5)) for age in ages])

        model, info=get_image_file_info(file_path)
        camera_models.append(model)
        camera_info.append(info)
        #print(fpath,model,info)
                
        print ('Processed photos: %d/%d\r'%(i+1,len(new_files)),end='')
        sys.stdout.flush()

    update_features=len(new_files)>0
    if False:
        camera_models=[]
        camera_info=[]
        for i,fpath in enumerate(files):
            file_path=os.path.join(album_dir,fpath)
            model, info=get_image_file_info(file_path)
            print(fpath,model,info)
            camera_models.append(model)
            camera_info.append(info)
        update_features=True
        
    if update_features:
        with open(features_file, "wb") as f:
            pickle.dump(files,f)
            pickle.dump(mdates, f)
            pickle.dump(all_facial_images,f)
            pickle.dump(all_born_years, f)
            pickle.dump(all_genders,f)
            pickle.dump(all_ethnicities,f)
            pickle.dump(all_features,f)
            pickle.dump(all_indices,f)
            pickle.dump(private_photo_indices, f)
            pickle.dump(camera_models, f)
            pickle.dump(camera_info, f)
        print('features dumped into',features_file)

    elapsed = time.time() - t
    no_image_files=len(files)
    print('\n %d facial photos found, elapsed=%f for processing of %d files'%(len(all_facial_images), elapsed,no_image_files))            

    #process video files
    if processVideoFiles!=0:
        video_files=[f for f in next(os.walk(album_dir))[2] if is_video(f)]
    else:
        video_files=[]
    video_mdates=[time.gmtime(os.path.getmtime(os.path.join(album_dir,f))) for f in video_files]
    t=time.time()
    for i,fpath in enumerate(video_files):
        facial_images,ages,genders,ethnicities,facial_features,has_center_face=process_video(imgProcessing,album_dir,fpath,video_mdates[i],None if video_bboxes is None else video_bboxes[i])
        if has_center_face:
            private_photo_indices.append(i+no_image_files)
        all_facial_images.extend(facial_images)
        #print('video genders:',genders)
        all_genders.extend(genders)
        all_ethnicities.extend(ethnicities)
        for features in facial_features:
            features=features/np.sqrt(np.sum(features**2))
            all_features.append(features)
        all_indices.extend([i+no_image_files]*len(ages))

        photo_year=video_mdates[i].tm_year+(video_mdates[i].tm_mon-1)/12
        all_born_years.extend([(photo_year-(age-0.5)) for age in ages])
            
    elapsed = time.time() - t

    files.extend(video_files)
    mdates.extend(video_mdates)
    all_born_years=np.array(all_born_years)
    all_genders=np.array(all_genders)
    all_ethnicities=np.array(all_ethnicities)
    all_features=np.array(all_features)

    print('\nelapsed=%f for processing of %d videos'%(elapsed,len(video_files)))         
    print(all_born_years.shape,all_genders.shape,all_features.shape,len(all_indices))
    
    filtered_clusters=perform_clustering(mdates,all_indices, all_features, all_born_years,minNoPhotos)
    #for ss in [[1, 995, 618, 75, 333, 334, 370, 371, 982, 791, 251, 1085],[1001],[212, 213]]:
    #    print([files[s] for s in ss])
    
    for video_id in range(no_image_files,len(files)):
        for face_ind in (ind for ind,file_id in enumerate(all_indices) if file_id==video_id):
            found=False
            for i,cluster in enumerate(filtered_clusters):
                if face_ind in cluster:
                    print(face_ind, ' for video ',video_id,' in cluster ', i)
                    found=True
                    break
            if not found:
                print('cluster for ',face_ind,' in video ',video_id,' not found')
    
    cluster_genders,cluster_born_years, cluster_ethnicities=[],[],[]
    #print('all_genders:',all_genders)
    displayed_clusters=[cluster for cluster in filtered_clusters if len(cluster)>=minNumberOfPhotosInDisplayedCluster]
    for i,cluster in enumerate(displayed_clusters):
        avg_gender_preds=np.median(all_genders[cluster])
        print(all_ethnicities[cluster].shape)
        avg_ethnicity_preds=np.mean(all_ethnicities[cluster],axis=0)
        avg_year=np.median(all_born_years[cluster])
        ds_gender=dempster_shafer_gender(all_genders[cluster])
        print('cluster ',i,avg_gender_preds,ds_gender,avg_year,avg_ethnicity_preds)
        #cluster_genders.append('male' if avg_gender_preds>=0.6 else 'female')
        cluster_genders.append('male' if ds_gender==0 else 'female')
        cluster_born_years.append(int(avg_year))
        cluster_ethnicities.append(FacialImageProcessing.get_ethnicity(avg_ethnicity_preds))

    private_photos=set([all_indices[elem] for cluster in filtered_clusters for elem in cluster]) | set(private_photo_indices)
    public_photo_path=[fpath for i,fpath in enumerate(files) if i not in private_photos and i<no_image_files]
    public_video_path=[fpath for i,fpath in enumerate(video_files) if (i+no_image_files) not in private_photos]

    model2info={}
    for i,(model,info) in enumerate(zip(camera_models,camera_info)):
        if model!='' and info!='':
            if model not in model2info:
                model2info[model]={}
            
            info=float(info)
            if info in model2info[model]:
                model2info[model][info]+=[i]
            else:
                model2info[model][info]=[i]
    #print(model2info)
    selfies=[]
    for model in model2info:
        min_info=min(model2info[model].keys())
        print(model,min_info,len(model2info[model][min_info]),len(model2info[model]))
        if len(model2info[model])>1:
            selfies+=model2info[model][min_info]
    selfies=set(selfies)
    print('selfies:',len(selfies))
    
    num_selfies_per_cluster=np.zeros(len(displayed_clusters))
    for i,cluster in enumerate(displayed_clusters):
        selfies_in_cluster=set([all_indices[elem] for elem in cluster])&selfies
        num_selfies_per_cluster[i]=len(selfies_in_cluster)
        #print(i,[files[s] for s in selfies_in_cluster],len(cluster))

    sorted_indices=num_selfies_per_cluster.argsort()
    me=sorted_indices[-1] if len(sorted_indices)>0 else -1
    print('num_selfies_per_cluster:',num_selfies_per_cluster,me)
    if me>=0 and num_selfies_per_cluster[me]<=5:
        me=-1
    
    if save_facial_clusters:
        res_dir=os.path.join(album_dir,'clusters')
        if os.path.exists(res_dir):
            shutil.rmtree(res_dir,ignore_errors=True)
            time.sleep(2)
        for i,cluster in enumerate(displayed_clusters):
            if i==me:
                clust_dir=os.path.join(res_dir,'%d me %s %d %s'%(i,cluster_genders[i],cluster_born_years[i],cluster_ethnicities[i]))
            else:
                clust_dir=os.path.join(res_dir,'%d %s %d %s'%(i,cluster_genders[i],cluster_born_years[i],cluster_ethnicities[i]))
            os.makedirs(clust_dir)
            for ind in cluster:
                cv2.imwrite(os.path.join(clust_dir,'%d.jpg'%(ind)),all_facial_images[ind])

        if save_public_photos:
            dst_dir=os.path.join(res_dir,'public')
            os.makedirs(dst_dir)
            for fpath in public_photo_path:
                full_photo = cv2.imread(os.path.join(album_dir,fpath))
                r = 200.0 / full_photo.shape[1]
                dim = (200, int(full_photo.shape[0] * r))
                full_photo=cv2.resize(full_photo, dim)
                cv2.imwrite(os.path.join(dst_dir,fpath),full_photo)
            for fpath in public_video_path:
                shutil.copy(os.path.join(album_dir,fpath),dst_dir)
        
    if no_clusters_to_show>0:
        no_clusters=min(no_clusters_to_show,len(displayed_clusters))
        plt_ind=1
        for i in range(no_clusters):
            for j in range(minNoPhotos):
                plt.subplot(no_clusters,minNoPhotos,plt_ind)
                plt.imshow(cv2.cvtColor(all_facial_images[displayed_clusters[i][j]],cv2.COLOR_BGR2RGB))
                plt.axis('off')
                plt_ind+=1

        plt.show()
    return public_photo_path,public_video_path,cluster_genders,cluster_born_years

def display_demography(cluster_genders,cluster_born_years):
    print('Sociality (number of closed persons)',len(cluster_genders))
    #print(cluster_genders,cluster_born_years)
    today_year = datetime.date.today().year
    male_ages=np.array([today_year-cluster_born_years[i] for i,gender in enumerate(cluster_genders) if gender=='male'])
    female_ages=np.array([today_year-cluster_born_years[i] for i,gender in enumerate(cluster_genders) if gender=='female'])
    
    max_age_ranges=[0,7,12,17,24,34,44,54,100]
    
    children_max_ind=2
    males_histo,females_histo=[],[]
    for i in range(1,len(max_age_ranges)):
        num_males=np.logical_and(male_ages>max_age_ranges[i-1],male_ages<=max_age_ranges[i]).sum()
        males_histo.append(num_males)
        
        num_females=np.logical_and(female_ages>max_age_ranges[i-1],female_ages<=max_age_ranges[i]).sum()
        females_histo.append(num_females)
        
        if i<children_max_ind:
            print('age range (%d,%d): %d children'%(max_age_ranges[i-1],max_age_ranges[i],num_males+num_females))
        else:
            print('age range (%d,%d): %d male(s), %d female(s)'%(max_age_ranges[i-1],max_age_ranges[i],num_males,num_females))
    
    males_histo=np.array(males_histo)
    females_histo=np.array(females_histo)
    #plot histo
    ind = np.arange(len(max_age_ranges)-1)
    width = 0.35       # the width of the bars
    fig, ax = plt.subplots()
    bars_men=ax.bar(ind,males_histo,width,color='b')
    bars_women=ax.bar(ind+width,females_histo,width,color='r')
    ax.set_ylabel('Count')
    ax.set_title('Demography\nSociality (number of closed persons):%d'%(len(cluster_genders)))
    ax.set_xticks(ind + width / 2)
    ax.set_xticklabels(['%d-%d'%(max_age_ranges[i-1],max_age_ranges[i]) for i in range(1,len(max_age_ranges))])
    ax.legend((bars_men[0], bars_women[0]), ('Male', 'Female'))
    ax.set_ylim([0, max(males_histo.max(),females_histo.max())])

    plt.show()
    return
    
if __name__ == '__main__':
    config=load_configs('../pipeline_config.txt')
    album=config['InputDirectory']
    imgProcessing=FacialImageProcessing(print_stat=False,minsize = 112)
    album=process_album(imgProcessing,album)
    imgProcessing.close()
