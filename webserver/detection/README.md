Source code for general object detection

Required libraries: Tensorflow, TensorFlow Models, OpenCV, MatplotLib

Run code:
python detect_objects.py # object detection in video from web camera
python detect_objects.py img_dir # object detection in all image files from img_dir directory

Before usage, set path to tensorflow object_detection folder in TF_MODELS_DIR environment variable.

This script can be used with models from [Tensorflow detection model zoo](https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md).
