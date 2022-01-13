Source code for visual data processing.

Required libraries: Tensorflow 2.x, flask, flask_cors, opencv-python, SciPy, scikit-image, SKLearn, MatplotLib, Pandas, TQDM, PyTorch, pretrainedmodels, efficientnet, xgboost, tabulate, albumentations, pytesseract, shapely, nltk, google-cloud-vision

Please install [Tensorflow Models](https://github.com/tensorflow/models) and assign environment variable TF_MODELS_DIR to the absolute path of 'models/research' directory. Follow installation instructions for [Object detection](https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/installation.md). In order to run this code for Tensorflow 2.x, it is necessary to add the following lines before load_labbelmap(path) function in research/object_detection/utils/label_map_util.py:

```python
if tf.__version__>='2.':
    tf.gfile=tf.io.gfile
```

Before usage, it is required to download models trained on OID v4 dataset from [Tensorflow detection model zoo](https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md) into any directory. At least 'ssd_resnet101_v1_fpn_shared_box_predictor_oid_512x512_sync_2019_01_20' and 'faster_rcnn_inception_resnet_v2_atrous_oid_v4_2018_12_12' models are required.
Configuration parameters are set in pipeline_config.txt. It is necessary to set at least ObjectDetectionModelsPath variable to refer to the directory with TF object detection models. In order to launch pipeline.py, assign path to photo gallery into variable InputDirectory.
In order to process public photos from Instagram account, use the following instruction


```
python instagram_parser.py --maximum=0 [instagram_account]
```


In order to run Web server demo, please refer to [instructions](instagram_parser_demo/README.md)
