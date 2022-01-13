import os,sys
import json
import numpy as np
import tensorflow.compat.v1 as tf

TPU_MODELS_PATH='/opt/lab/tpu/models'
sys.path.insert(0,os.path.join(TPU_MODELS_PATH,'official/efficientnet'))
sys.path.insert(0,os.path.join(TPU_MODELS_PATH,'common'))

import efficientnet_builder
import preprocessing
import utils

class EfficientNet():
    def __init__(self,model_name='efficientnet-b0',ckpt_dir=None):
        self.model_name = model_name
        self.ckpt_dir=ckpt_dir if ckpt_dir is not None else model_name
        _, _, self.image_size, _ = efficientnet_builder.efficientnet_params(model_name)
        
        with tf.Graph().as_default() as graph:
            config = tf.ConfigProto()
            config.gpu_options.allow_growth = True
            self.sess=tf.Session(config=config)
            self.filename=tf.placeholder(tf.string)
            image_string = tf.read_file(self.filename)
            preprocess_fn = self.get_preprocess_fn()
            image_decoded = preprocess_fn(image_string, False, image_size=self.image_size)
            image = tf.cast(image_decoded, tf.float32)
            images=tf.expand_dims(image, 0)
            self.probs = self.build_model(images)
            self.restore_model()

    def restore_model(self, enable_ema=True):
        """Restore variables from checkpoint dir."""
        self.sess.run(tf.global_variables_initializer())
        checkpoint = tf.train.latest_checkpoint(os.path.join(TPU_MODELS_PATH,'official/efficientnet/checkpoints/',self.ckpt_dir))
        if enable_ema:
            ema = tf.train.ExponentialMovingAverage(decay=0.0)
            ema_vars = utils.get_ema_vars()
            var_dict = ema.variables_to_restore(ema_vars)
            ema_assign_op = ema.apply(ema_vars)
        else:
            var_dict = get_ema_vars()
            ema_assign_op = None

        tf.train.get_or_create_global_step()
        self.sess.run(tf.global_variables_initializer())
        saver = tf.train.Saver(var_dict, max_to_keep=1)
        saver.restore(self.sess, checkpoint)
          
    def build_model(self, images):
        images -= tf.constant(
          efficientnet_builder.MEAN_RGB, shape=[1, 1, 3], dtype=images.dtype)
        images /= tf.constant(
          efficientnet_builder.STDDEV_RGB, shape=[1, 1, 3], dtype=images.dtype)
        features, _ = efficientnet_builder.build_model(
            images, self.model_name, False, pooled_features_only=True)
        return features

    def get_preprocess_fn(self):
        return preprocessing.preprocess_image
    
    def extract_features(self,image_file):
        return self.sess.run(self.probs, feed_dict={self.filename:image_file})
