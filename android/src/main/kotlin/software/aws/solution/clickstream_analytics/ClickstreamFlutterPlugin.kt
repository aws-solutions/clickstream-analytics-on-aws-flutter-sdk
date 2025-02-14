/*
 * Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
 
package software.aws.solution.clickstream_analytics

import android.app.Activity
import com.amazonaws.logging.Log
import com.amazonaws.logging.LogFactory
import com.amplifyframework.core.Amplify
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import software.aws.solution.clickstream.ClickstreamAnalytics
import software.aws.solution.clickstream.ClickstreamAttribute
import software.aws.solution.clickstream.ClickstreamConfiguration
import software.aws.solution.clickstream.ClickstreamEvent
import software.aws.solution.clickstream.ClickstreamItem
import software.aws.solution.clickstream.ClickstreamUserAttribute
import software.aws.solution.clickstream.client.util.ThreadUtil
import java.util.Objects
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/** ClickstreamFlutterPlugin */
class ClickstreamFlutterPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity

    private val cachedThreadPool: ExecutorService by lazy { Executors.newCachedThreadPool() }

    private val log: Log = LogFactory.getLog(
        ClickstreamFlutterPlugin::class.java
    )
    private lateinit var channel: MethodChannel

    private var mActivity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "clickstream_flutter")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        val arguments = call.arguments() as HashMap<String, Any>?
        when (call.method) {
            "init" -> result.success(initSDK(arguments!!))
            "record" -> recordEvent(arguments)
            "setUserId" -> setUserId(arguments)
            "setUserAttributes" -> setUserAttributes(arguments)
            "addGlobalAttributes" -> addGlobalAttributes(arguments)
            "deleteGlobalAttributes" -> deleteGlobalAttributes(arguments)
            "updateConfigure" -> updateConfigure(arguments)
            "flushEvents" -> ClickstreamAnalytics.flushEvents()
            "disable" -> ClickstreamAnalytics.disable()
            "enable" -> ClickstreamAnalytics.enable()
            else -> result.notImplemented()
        }
    }

    private fun initSDK(arguments: HashMap<String, Any>): Boolean {
        if (getIsInitialized()) return false
        if (mActivity != null) {
            val context = mActivity!!.applicationContext
            if (ThreadUtil.notInMainThread()) {
                log.error("Clickstream SDK initialization failed, please initialize in the main thread")
                return false
            }
            val sessionTimeoutDuration = arguments["sessionTimeoutDuration"]
                .let { (it as? Int)?.toLong() ?: (it as Long) }
            val sendEventsInterval = arguments["sendEventsInterval"]
                .let { (it as? Int)?.toLong() ?: (it as Long) }
            val configuration = ClickstreamConfiguration()
                .withAppId(arguments["appId"] as String)
                .withEndpoint(arguments["endpoint"] as String)
                .withLogEvents(arguments["isLogEvents"] as Boolean)
                .withTrackScreenViewEvents(arguments["isTrackScreenViewEvents"] as Boolean)
                .withTrackUserEngagementEvents(arguments["isTrackUserEngagementEvents"] as Boolean)
                .withTrackAppExceptionEvents(arguments["isTrackAppExceptionEvents"] as Boolean)
                .withSendEventsInterval(sendEventsInterval)
                .withSessionTimeoutDuration(sessionTimeoutDuration)
                .withCompressEvents(arguments["isCompressEvents"] as Boolean)
                .withAuthCookie(arguments["authCookie"] as String)

            (arguments["globalAttributes"] as? HashMap<*, *>)?.takeIf { it.isNotEmpty() }
                ?.let { attributes ->
                    val globalAttributes = ClickstreamAttribute.builder()
                    for ((key, value) in attributes) {
                        when (value) {
                            is String -> globalAttributes.add(key.toString(), value)
                            is Double -> globalAttributes.add(key.toString(), value)
                            is Boolean -> globalAttributes.add(key.toString(), value)
                            is Int -> globalAttributes.add(key.toString(), value)
                            is Long -> globalAttributes.add(key.toString(), value)
                        }
                    }
                    configuration.withInitialGlobalAttributes(globalAttributes.build())
                }
            return try {
                ClickstreamAnalytics.init(context, configuration)
                true
            } catch (exception: Exception) {
                log.error("Clickstream SDK initialization failed with error: " + exception.message)
                false
            }
        } else {
            return false
        }
    }

    private fun recordEvent(arguments: HashMap<String, Any>?) {
        cachedThreadPool.execute {
            arguments?.let { processEvent(it) }
        }
    }

    private fun processEvent(arguments: HashMap<String, Any>) {
        val eventName = arguments["eventName"] as String
        val attributes = arguments["attributes"] as HashMap<*, *>
        val items = arguments["items"] as ArrayList<*>

        val eventBuilder = ClickstreamEvent.builder()
            .name(eventName)
            .apply { addEventAttributes(this, attributes) }
            .apply { addItemsIfPresent(this, items) }

        ClickstreamAnalytics.recordEvent(eventBuilder.build())
    }

    private fun addEventAttributes(builder: ClickstreamEvent.Builder, attributes: HashMap<*, *>) {
        attributes.forEach { (key, value) ->
            addEventAttribute(builder, key.toString(), value)
        }
    }

    private fun addEventAttribute(builder: ClickstreamEvent.Builder, key: String, value: Any?) {
        when (value) {
            is String -> builder.add(key, value)
            is Double -> builder.add(key, value)
            is Boolean -> builder.add(key, value)
            is Int -> builder.add(key, value)
            is Long -> builder.add(key, value)
        }
    }

    private fun addItemsIfPresent(eventBuilder: ClickstreamEvent.Builder, items: ArrayList<*>) {
        if (items.isEmpty()) return

        val clickstreamItems = items.map { createClickstreamItem(it as HashMap<*, *>) }
            .toTypedArray()

        eventBuilder.setItems(clickstreamItems)
    }

    private fun createClickstreamItem(itemMap: HashMap<*, *>): ClickstreamItem {
        return ClickstreamItem.builder()
            .apply { addItemAttributes(this, itemMap) }
            .build()
    }

    private fun addItemAttributes(builder: ClickstreamItem.Builder, attributes: HashMap<*, *>) {
        attributes.forEach { (key, value) ->
            addItemAttribute(builder, key.toString(), value)
        }
    }

    private fun addItemAttribute(builder: ClickstreamItem.Builder, key: String, value: Any?) {
        when (value) {
            is String -> builder.add(key, value)
            is Double -> builder.add(key, value)
            is Boolean -> builder.add(key, value)
            is Int -> builder.add(key, value)
            is Long -> builder.add(key, value)
        }
    }


    private fun setUserId(arguments: java.util.HashMap<String, Any>?) {
        arguments?.let {
            val userId = arguments["userId"]
            if (userId == null) {
                ClickstreamAnalytics.setUserId(null)
            } else {
                ClickstreamAnalytics.setUserId(userId.toString())
            }
        }
    }

    private fun setUserAttributes(arguments: java.util.HashMap<String, Any>?) {
        arguments?.let {
            val builder = ClickstreamUserAttribute.Builder()
            for ((key, value) in arguments) {
                when (value) {
                    is String -> builder.add(key, value)
                    is Double -> builder.add(key, value)
                    is Boolean -> builder.add(key, value)
                    is Int -> builder.add(key, value)
                    is Long -> builder.add(key, value)
                }
            }
            ClickstreamAnalytics.addUserAttributes(builder.build())
        }
    }

    private fun addGlobalAttributes(arguments: java.util.HashMap<String, Any>?) {
        arguments?.let {
            val builder = ClickstreamAttribute.Builder()
            for ((key, value) in arguments) {
                when (value) {
                    is String -> builder.add(key, value)
                    is Double -> builder.add(key, value)
                    is Boolean -> builder.add(key, value)
                    is Int -> builder.add(key, value)
                    is Long -> builder.add(key, value)
                }
            }
            ClickstreamAnalytics.addGlobalAttributes(builder.build())
        }
    }

    private fun deleteGlobalAttributes(arguments: java.util.HashMap<String, Any>?) {
        arguments?.let {
            @Suppress("UNCHECKED_CAST")
            val attributes = arguments["attributes"] as ArrayList<String>
            ClickstreamAnalytics.deleteGlobalAttributes(*attributes.toTypedArray())
        }
    }

    private fun updateConfigure(arguments: java.util.HashMap<String, Any>?) {
        arguments?.let {
            val configure = ClickstreamAnalytics.getClickStreamConfiguration()
            arguments["appId"]?.let {
                configure.withAppId(it as String)
            }
            arguments["endpoint"]?.let {
                configure.withEndpoint(it as String)
            }
            arguments["isLogEvents"]?.let {
                configure.withLogEvents(it as Boolean)
            }
            arguments["isTrackScreenViewEvents"]?.let {
                configure.withTrackScreenViewEvents(it as Boolean)
            }
            arguments["isTrackUserEngagementEvents"]?.let {
                configure.withTrackUserEngagementEvents(it as Boolean)
            }
            arguments["isTrackAppExceptionEvents"]?.let {
                configure.withTrackAppExceptionEvents(it as Boolean)
            }
            arguments["sessionTimeoutDuration"]?.let {
                val sessionTimeoutDuration = arguments["sessionTimeoutDuration"]
                    .let { (it as? Int)?.toLong() ?: (it as Long) }
                configure.withSessionTimeoutDuration(sessionTimeoutDuration)
            }
            arguments["isCompressEvents"]?.let {
                configure.withCompressEvents(it as Boolean)
            }
            arguments["authCookie"]?.let {
                configure.withAuthCookie(it as String)
            }
        }
    }

    private fun getIsInitialized(): Boolean {
        return invokeSuperMethod(Amplify.Analytics, "isConfigured") as Boolean
    }

    @Throws(Exception::class)
    fun invokeSuperMethod(`object`: Any, methodName: String): Any? {
        val method =
            Objects.requireNonNull(`object`.javaClass.superclass).getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(`object`)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mActivity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        // The Activity that this plugin was attached to was destroyed to
        // change configuration. By the end of this method, the Activity that
        // was made available in onAttachedToActivity() is no longer valid.
        // Any references to the associated Activity should be cleared.
        // Reference: https://api.flutter.dev/javadoc/io/flutter/embedding/engine/plugins/activity/ActivityAware.html#onDetachedFromActivityForConfigChanges()
        mActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        mActivity = binding.activity
    }

    override fun onDetachedFromActivity() {
        mActivity = null
    }
}