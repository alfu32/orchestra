# TestProject Functional Specification

Generated from Threadwork specifications for `/TestProject`.

## Processing Nodes

### TestProject

- **Path:** `/TestProject`
- **Role:** `CompositeWorker`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

_None._

### read_config

- **Path:** `/TestProject/read_config`
- **Role:** `Generator`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| config | Unspecified | read_info_packages | out |
| config_error | Unspecified | error_log | out |

### process_data

- **Path:** `/TestProject/process_data`
- **Role:** `CompositeWorker`

#### Specification

jgjgjgjgj

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| read_info_packages -> process_data | Unspecified | read_info_packages | in |
| retry_failed_packages -> process_data | Unspecified | retry_failed_packages | in |
| send_results -> process_data | Unspecified | send_results | in |

#### Outputs

_None._

### remove_duplicates

- **Path:** `/TestProject/process_data/remove_duplicates`
- **Role:** `Transformer`

#### Specification

_No functional specification provided._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| read_info_packages -> process_data -> remove_duplicates | Unspecified | read_info_packages -> process_data | in |
| retry_failed_packages -> process_data -> remove_duplicates | Unspecified | retry_failed_packages -> process_data | in |

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| valid_info_package | Unspecified | process_data | out |

### process_data

- **Path:** `/TestProject/process_data/process_data`
- **Role:** `Sink`

#### Specification

_No functional specification provided._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| send_results -> process_data -> process_data | Unspecified | send_results -> process_data | in |
| valid_info_package | Unspecified | remove_duplicates | in |

#### Outputs

_None._

### read_info_packages

- **Path:** `/TestProject/read_info_packages`
- **Role:** `Transformer`

#### Specification

_No functional specification provided._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| config | Unspecified | read_config | in |

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| info_package_error | Unspecified | error_log | out |
| read_info_packages -> process_data | Unspecified | process_data | out |

### send_results

- **Path:** `/TestProject/send_results`
- **Role:** `Generator`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| send_results -> process_data | Unspecified | process_data | out |
| send_results_error | Unspecified | error_log | out |

### lib_io_file

- **Path:** `/TestProject/lib_io_file`
- **Role:** `ServiceLibrary`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

_None._

### lib_soap_sm

- **Path:** `/TestProject/lib_soap_sm`
- **Role:** `ServiceLibrary`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

_None._

### lib_logging

- **Path:** `/TestProject/lib_logging`
- **Role:** `ServiceLibrary`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

_None._

### error_log

- **Path:** `/TestProject/error_log`
- **Role:** `ErrorHandler`

#### Specification

_No functional specification provided._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| config_error | Unspecified | read_config | in |
| info_package_error | Unspecified | read_info_packages | in |
| send_results_error | Unspecified | send_results | in |

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| failed_info_package | Unspecified | retry_failed_packages | out |

### retry_failed_packages

- **Path:** `/TestProject/retry_failed_packages`
- **Role:** `Transformer`

#### Specification

_No functional specification provided._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| failed_info_package | Unspecified | error_log | in |

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| retry_failed_packages -> process_data | Unspecified | process_data | out |

### config.json

- **Path:** `/TestProject/config.json`
- **Role:** `ProcessingUnit`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

_None._

### package.json

- **Path:** `/TestProject/package.json`
- **Role:** `ProcessingUnit`

#### Specification

_No functional specification provided._

#### Inputs

_None._

#### Outputs

_None._

## Data Flows

### config

- **Link ID:** `link_14`
- **From:** `read_config.out`
- **To:** `read_info_packages.in`
- **Classification:** `Transport`

### config_error

- **Link ID:** `link_18`
- **From:** `read_config.out`
- **To:** `error_log.in`
- **Classification:** `ErrorPipe`

### failed_info_package

- **Link ID:** `link_23`
- **From:** `error_log.out`
- **To:** `retry_failed_packages.in`
- **Classification:** `Transport`

### info_package_error

- **Link ID:** `link_19`
- **From:** `read_info_packages.out`
- **To:** `error_log.in`
- **Classification:** `ErrorPipe`

### lib_io_file -> read_config

- **Link ID:** `link_8`
- **From:** `lib_io_file.out`
- **To:** `read_config.in`
- **Classification:** `UsageImport`

### lib_logging -> read_config

- **Link ID:** `link_10`
- **From:** `lib_logging.out`
- **To:** `read_config.in`
- **Classification:** `UsageImport`

### lib_logging -> read_infop

- **Link ID:** `link_11`
- **From:** `lib_logging.out`
- **To:** `read_info_packages.in`
- **Classification:** `UsageImport`

### lib_logging -> send_results

- **Link ID:** `link_12`
- **From:** `lib_logging.out`
- **To:** `send_results.in`
- **Classification:** `UsageImport`

### lib_soap_sm -> read_infop

- **Link ID:** `link_9`
- **From:** `lib_soap_sm.out`
- **To:** `read_info_packages.in`
- **Classification:** `UsageImport`

### lib_soap_sm -> send_results

- **Link ID:** `link_13`
- **From:** `lib_soap_sm.out`
- **To:** `send_results.in`
- **Classification:** `UsageImport`

### read_info_packages -> process_data

- **Link ID:** `link_14d701b7-ae01-4f47-a887-a18f6b250f8e`
- **From:** `read_info_packages.out`
- **To:** `process_data.in`
- **Classification:** `Transport`

### retry_failed_packages -> process_data

- **Link ID:** `link_11b70edf-67a9-4080-a4e1-7e0613453d1c`
- **From:** `retry_failed_packages.out`
- **To:** `process_data.in`
- **Classification:** `Transport`

### send_results -> process_data

- **Link ID:** `link_afbf950c-3073-4552-8157-432a3aa2ad3a`
- **From:** `send_results.out`
- **To:** `process_data.in`
- **Classification:** `Transport`

### send_results_error

- **Link ID:** `link_21`
- **From:** `send_results.out`
- **To:** `error_log.in`
- **Classification:** `ErrorPipe`

### read_info_packages -> process_data -> remove_duplicates

- **Link ID:** `link_3d77721c-20e1-4c43-9770-d030d994e18e`
- **From:** `read_info_packages -> process_data.out`
- **To:** `remove_duplicates.in`
- **Classification:** `Transport`

### retry_failed_packages -> process_data -> remove_duplicates

- **Link ID:** `link_802d7da8-d815-4f59-b30d-e0e47ff93a36`
- **From:** `retry_failed_packages -> process_data.out`
- **To:** `remove_duplicates.in`
- **Classification:** `Transport`

### send_results -> process_data -> process_data

- **Link ID:** `link_5b65bb8d-20a1-487f-92bd-7cb17f92e78d`
- **From:** `send_results -> process_data.out`
- **To:** `process_data.in`
- **Classification:** `Transport`

### valid_info_package

- **Link ID:** `link_30`
- **From:** `remove_duplicates.out`
- **To:** `process_data.in`
- **Classification:** `Transport`
