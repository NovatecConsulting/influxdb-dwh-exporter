# InfluxDB DWH Exporter

This application provides an HTTP endpoint that can deliver metrics from an InfluxDB in an aggregated form when called.

This can be used to provide an aggregated view of existing metrics or derived ones to an external tool (e.g. data warehouse or other databases).

## Usage

The endpoint which is provided and through which the metrics can be retrieved/exported is `/dwh`

| Parameter | Example | Description |
| ------------- |:-------------:| -----:|
| `token`| `/dwh?token=my_secret`| `token` must match the token configured in the `application.yml`, otherwise access will be denied. |
| `interval`| `/dwh?interval=5m`| Specifies the resolution in which the metrics are provided. This corresponds to the `${interval}` variable in the configured queries. If interval is not specified, the default value `1m` is used, i.e. a resolution per minute. |
| `range`| `/dwh?range=2h`| Defines the time period that the metrics should cover. For example, `2h` means that the data points of the metrics of the last two hours should be queried. This variable is used to define `${timeFilter}` in the configured queries. If range is not specified, the data for one interval will be returned. |
| `offset`| `/dwh?offset=5m`| Defines a time offset with which the data should be queried. For example, if the data is queried at 15:00, it is possible that the data for 14:59 has not yet arrived in the InfluxDB. By using offset, the query interval can now be shifted into the past. If offset is not defined, an offset of one minute is used. |
| `start` & `end`| `/dwh?start=1587023100000&end=1587023400000`| Alternative way to specify the time period compared to range and offset. The values start and end must be epoch timestamps in milliseconds. It is also important that both start and end are multiples of the interval. I.e. for example with an interval of 1m both timestamps must be divisible by 60000 without remainder. |

#### Examples

`/dwh?token=my_secret` - Returns the data of the last minute in minute resolution with an offset of one minute.

`/dwh?token=my_secret&interval=15m` - Returns the data of the last 15 minutes in a 15-minute resolution with an offset of one minute.

`/dwh?token=my_secret&interval=15m&range=60m&offset=5m` - Returns the data of the last 60 minutes in a 15-minute resolution with an offset of five minutes.

`/dwh?token=my_secret&interval=1m&start=1587023340000&end1587023400000` - Returns the data between defined the timestamps in a 1-minute resolution.

## Configuration

The configuration of the provided metrics can be done via an `application.yml` file, which can be put in the working directory of the application.

The following code is an example for the `application.yml`.
For a full list of available configurations, see the default [application.yml](https://github.com/NovatecConsulting/influxdb-dwh-exporter/blob/main/src/main/resources/application.yml):

```yaml
server:
  # the port of the server
  port: 8080

influx:
  url: http://localhost:8086
  username: ""
  password: ""
  token: ""

dwh:
  # secret to access the /dwh endpoint
  token: my_secret

  # the metrics and related queries to use and export
  metrics:
    - name: my-metric|${service}|${http_path}
      database: inspectit # also the bucket name
      query: |
        SELECT SUM("sum") / SUM("count")
        FROM "inspectit"."autogen"."http_in_responsetime"
        GROUP BY time(${interval}), "service", "http_path" FILL(null)
```

### Metrics Configuration

A list of metrics that will be provided via the HTTP endpoint can be specified in `dwh.metrics`.
Two parameters must be defined for each metric: `name`, `database` and `query`.

`name` is the name of the resulting "metric view".
The name can be parameterized using the tags of the underlying metric, as shown in the example above using `${service}` and `${http_path}`.

`database` is the name of the database in InfluxDB. For InfluxDB version 2, use the bucket name.

`query` is the InfluxDB query that is executed to retrieve the corresponding metric from InfluxDB.
Here, the variable `${timeFilter}` should be used in the WHERE clause.
Also, `{interval}` should be used as "GROUP BY time" interval!

It is important that **exactly** the tags used for parameterization in `name` are also used in the GROUP BY-clause!

When the endpoint provided by the application is called, the exporter executes **all** configured queries and returns the corresponding result.

### Health Endpoint

The application provides an endpoint under `/actuator/health that can be used to check if the application is running and the InfluxDB is available.

By default, only a simple status is provided:
```
{
    "status":"UP",
}
```

However, with the following configuration, additional information, such as status of the InfluxDB connection, can be obtained via the health endpoint:
```
management:
   endpoint:
      health:
        show-details: "ALWAYS"
```

Using this configuration, the health endpoint provides the following result:
```
{
    "status":"UP",
    "components": {
        "influxDb": {
            "status":"UP"
        }
    }
}
```

## SBOM

To generate a software bill of materials (SBOM), execute the gradle task `cyclonedxBom`.
It will save the BOM into the folder build/reports.

##### How to Release

Important tasks to check first are `dependencyUpdates` and `dependencyUpdates[Major|Minor]` for newer (patch, minor, major)
versions and `dependencyCheckAnalyze` for security issues in the used dependencies. 
