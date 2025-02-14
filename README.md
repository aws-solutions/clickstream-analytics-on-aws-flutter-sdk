# AWS Solution Clickstream Analytics SDK for Flutter

## Introduction

Clickstream Flutter SDK can help you easily collect and report events from your mobile app to AWS. This SDK is part of an AWS solution - [Clickstream Analytics on AWS](https://github.com/aws-solutions/clickstream-analytics-on-aws), which provisions data pipeline to ingest and process event data into AWS services such as S3, Redshift.

The SDK relies on the [Clickstream Android SDK](https://github.com/aws-solutions/clickstream-analytics-on-aws-android-sdk) and [Clickstream Swift SDK](https://github.com/aws-solutions/clickstream-analytics-on-aws-swift-sdk). Therefore, flutter SDK also supports automatically collect common user events and attributes (e.g., session start, first open). In addition, we've added easy-to-use APIs to simplify data collection in Flutter apps.

Visit our [Documentation site](https://aws-solutions.github.io/clickstream-analytics-on-aws/en/latest/sdk-manual/flutter/) to learn more about Clickstream Flutter SDK.

## Integrate SDK

### Include SDK

```bash
flutter pub add clickstream_analytics
```

After complete, rebuild your Flutter application:

```bash
flutter run
```

### Initialize the SDK

Copy your configuration code from your clickstream solution web console, the configuration code should look like as follows. You can also manually add this code snippet and replace the values of appId and endpoint after you registered app to a data pipeline in the Clickstream Analytics solution console.

```dart
import 'package:clickstream_analytics/clickstream_analytics.dart';

final analytics = ClickstreamAnalytics();
analytics.init(
  appId: "your appId",
  endpoint: "https://example.com/collect"
);
```

Please note：

1. Your `appId` and `endpoint` are already set up in it.
2. We only need to initialize the SDK once after the application starts. It is recommended to do it in the main function of your App.
3. We can use `bool result = await analytics.init()` to get the boolean value of the initialization result.

### Start using

#### Record event

Add the following code where you need to record event.

```dart
import 'package:clickstream_analytics/clickstream_analytics.dart';

final analytics = ClickstreamAnalytics();

// record event with attributes
analytics.record(name: 'button_click', attributes: {
  "event_category": "shoes",
  "currency": "CNY",
  "value": 279.9
});

//record event with name
analytics.record(name: "button_click");
```

#### Login and logout

```dart
/// when user login success.
analytics.setUserId("userId");

/// when user logout
analytics.setUserId(null);
```

#### Add user attribute

```dart
analytics.setUserAttributes({
  "userName": "carl",
  "userAge": 22
});
```

Current login user's attributes will be cached in disk, so the next time app launch you don't need to set all user's attribute again, of course you can use the same api `analytics.setUserAttributes()` to update the current user's attribute when it changes.

#### Add global attribute

1. Add global attributes when initializing the SDK
   
   The following example code shows how to add traffic source fields as global attributes when initializing the SDK.
   ```dart
   analytics.init({
     appId: "your appId",
     endpoint: "https://example.com/collect",
     globalAttributes: {
       Attr.TRAFFIC_SOURCE_SOURCE: "amazon",
       Attr.TRAFFIC_SOURCE_MEDIUM: "cpc",
       Attr.TRAFFIC_SOURCE_CAMPAIGN: "summer_promotion",
       Attr.TRAFFIC_SOURCE_CAMPAIGN_ID: "summer_promotion_01",
       Attr.TRAFFIC_SOURCE_TERM: "running_shoes",
       Attr.TRAFFIC_SOURCE_CONTENT: "banner_ad_1",
       Attr.TRAFFIC_SOURCE_CLID: "amazon_ad_123",
       Attr.TRAFFIC_SOURCE_CLID_PLATFORM: "amazon_ads",
       Attr.APP_INSTALL_CHANNEL: "amazon_store"
     }
   });
   ```

2. Add global attributes after initializing the SDK
   ```dart
   analytics.addGlobalAttributes({
     Attr.TRAFFIC_SOURCE_MEDIUM: "Search engine",
     "level": 10
   });
   ```

#### Delete global attribute
```
analytics.deleteGlobalAttributes(["level"]);
```

It is recommended to set global attributes after each SDK initialization, global attributes will be included in all events that occur after it is set.

#### Record event with items

You can add the following code to log an event with an item. you can add custom item attribute in `attributes` object.

**Note: Only pipelines from version 1.1+ can handle items with custom attribute.**

```dart
var itemBook = ClickstreamItem(
    id: "123",
    name: "Nature",
    category: "book",
    price: 99,
    attributes: {
      "book_publisher": "Nature Research"
    }
);

analytics.record(
    name: "view_item", 
    attributes: {
        Attr.VALUE: 99,
        Attr.CURRENCY: "USD"
        "event_category": "recommended"
    }, 
    items: [itemBook]
);
```

#### Record Screen View events manually

By default, SDK will automatically track the preset `_screen_view` event when Android Activity triggers `onResume` or iOS ViewController triggers `viewDidAppear`.

You can also manually record screen view events whether automatic screen view tracking is enabled, add the following code to record a screen view event with two attributes.

* `screenName` Required. Your screen's name.
* `screenUniqueId` Optional. Set the id of your Widget. If you do not set, the SDK will set a default value based on the hashcode of the current Activity or ViewController.

```dart
analytics.recordScreenView(
  screenName: 'Main',
  screenUniqueId: '123adf',
  attributes: { ... }
);
```

#### Other configurations

In addition to the required `appId` and `endpoint`, you can configure other information to get more customized usage:

```dart
final analytics = ClickstreamAnalytics();
analytics.init(
  appId: "your appId",
  endpoint: "https://example.com/collect",
  isLogEvents: false,
  isCompressEvents: false,
  sendEventsInterval: 10000,
  isTrackScreenViewEvents: true,
  isTrackUserEngagementEvents: true,
  isTrackAppExceptionEvents: false,
  authCookie: "your auth cookie",
  sessionTimeoutDuration: 1800000,
  globalAttributes: {
    "_traffic_source_medium": "Search engine",
  },
);
```

Here is an explanation of each option:

- **appId (Required)**: the app id of your project in control plane.
- **endpoint (Required)**: the endpoint path you will upload the event to Clickstream ingestion server.
- **isLogEvents**: whether to print out event json for debugging, default is false.
- **isCompressEvents**: whether to compress event content when uploading events, default is `true`
- **sendEventsInterval**: event sending interval millisecond, works only bath send mode, the default value is `5000`
- **isTrackScreenViewEvents**: whether auto record screen view events in app, default is `true`
- **isTrackUserEngagementEvents**: whether auto record user engagement events in app, default is `true`
- **isTrackAppExceptionEvents**: whether auto track exception event in app, default is `false`
- **authCookie**: your auth cookie for AWS application load balancer auth cookie.
- **sessionTimeoutDuration**: the duration for session timeout millisecond, default is 1800000
- **globalAttributes**: the global attributes when initializing the SDK.

#### Configuration update

You can update the default configuration after initializing the SDK, below are the additional configuration options you can customize.

```dart
final analytics = ClickstreamAnalytics();
analytics.updateConfigure(
    appId: "your appId",
    endpoint: "https://example.com/collect",
    isLogEvents: true,
    isCompressEvents: false,
    isTrackScreenViewEvents: false
    isTrackUserEngagementEvents: false,
    isTrackAppExceptionEvents: false,
    authCookie: "test cookie");
```

#### Send event immediately

```dart
final analytics = ClickstreamAnalytics();
analytics.flushEvents();
```

#### Disable SDK

You can disable the SDK in the scenario you need. After disabling the SDK, the SDK will not handle the logging and
sending of any events. Of course, you can enable the SDK when you need to continue logging events.

```dart
final analytics = ClickstreamAnalytics();

// disable SDK
analytics.disable();

// enable SDK
analytics.enable();
```

## How to build and test locally

### Build

Install flutter packages

```bash
flutter pub get
```

Build for Android 

```bash
cd example && flutter build apk
```

Build for iOS

```dart
cd example && flutter build ios
```

### Format and lint

```bash
dart format . && flutter analyze
```

### Test

```bash
flutter test
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This library is licensed under the [Apache 2.0 License](./LICENSE).

## Anonymous operational metrics

This solution collects anonymous operational metrics to help AWS improve the quality and features of the solution. For more information, including how to disable this capability, please see the [implementation guide](https://docs.aws.amazon.com/solutions/latest/clickstream-analytics-on-aws/solution-overview.html).