package com.arcsoft.arcfacedemo.common;

/**
 * 一些常量设置
 * <p>
 * 双目偏移可在 [参数设置] -> [识别界面适配] 界面中进行设置
 */
public class Constants {

    /**
     * 方式一： 填好APP_ID等参数，进入激活界面激活
     */
    public static final String APP_ID = "8BwekrCEpjxLbUUfVZKmAVt5VP95VJuDv9ZBGdNGt8bG";
    public static final String SDK_KEY = "Hfo3Win9CMTj3mGcnmmdx8u31kaJCnDG8tQMrW55xkue";
    public static final String ACTIVE_KEY = "85Q1-11G6-N11L-U5GC";
    /**
     * 方式二： 在激活界面读取本地配置文件进行激活
     *
     * 配置文件名称，格式如下：
     * APP_ID:XXXXXXXXXXXXX
     * SDK_KEY:XXXXXXXXXXXXXXX
     * ACTIVE_KEY:XXXX-XXXX-XXXX-XXXX
     */
    public static final String ACTIVE_CONFIG_FILE_NAME = "activeConfig.txt";


    /**
     * 注册图所在路径
     */
    public static final String DEFAULT_REGISTER_FACES_DIR = "sdcard/arcfacedemo/register";
}
