# Batch generator
Run Batch Workload Generator to generate workloads for all templates in `workload-templates` folder.
```shell
mvn -pl tool exec:java@generate-workloads
```

See `tool\src\test\resources\template.yaml` for template example.
