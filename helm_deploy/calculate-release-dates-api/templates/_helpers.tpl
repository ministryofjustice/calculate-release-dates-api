{{/* vim: set filetype=mustache: */}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "app.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Returns the name of the cronjob resource.
Cronjob resource names have a maximum length of 52 characters - see https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#:~:text=Even%20when%20the%20name%20is,no%20more%20than%2063%20characters

The default behaviour is to use the release name (truncated to 27) appended with `-data-analytics-extractor` giving a maximum length of 52.
This behaviour can be overriden by providing a `cronJobNameOverride`. If provided in the values it will be used, truncated at 52.
*/}}
{{- define "generic-data-analytics-extractor.cronjob-name" -}}
{{- if .Values.cronJobNameOverride -}}
{{- .Values.cronJobNameOverride | trunc 52 -}}
{{- else -}}
{{ .Release.Name | trunc 27 -}}-data-analytics-extractor-crds
{{- end -}}
{{- end -}}