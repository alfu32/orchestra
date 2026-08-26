# TestProject Technical Documentation

Generated from Threadwork technical metadata and contracts for `/TestProject`.

## Processing Nodes

### TestProject

- **Path:** `/TestProject`
- **Role:** `CompositeWorker`
- **Direct language:** `javascript`
- **Direct technology:** `nodejs`

#### Inputs

_None._

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### read_config

- **Path:** `/TestProject/read_config`
- **Role:** `Generator`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

_None._

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| config | Unspecified | read_info_packages | out |
| config_error | Unspecified | error_log | out |

#### Dependencies

| Instance | Library | Classification |
| --- | --- | --- |
| lib_io_file -> read_config | lib_io_file | UsageImport |
| lib_logging -> read_config | lib_logging | UsageImport |

#### Running and Compiling Instructions

_No instructions provided._

### process_data

- **Path:** `/TestProject/process_data`
- **Role:** `CompositeWorker`
- **Direct language:** `javascript`
- **Direct technology:** _Not specified directly._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| read_info_packages -> process_data | Unspecified | read_info_packages | in |
| retry_failed_packages -> process_data | Unspecified | retry_failed_packages | in |
| send_results -> process_data | Unspecified | send_results | in |

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

jggggggggggggg

### remove_duplicates

- **Path:** `/TestProject/process_data/remove_duplicates`
- **Role:** `Transformer`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| read_info_packages -> process_data -> remove_duplicates | Unspecified | read_info_packages -> process_data | in |
| retry_failed_packages -> process_data -> remove_duplicates | Unspecified | retry_failed_packages -> process_data | in |

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| valid_info_package | Unspecified | process_data | out |

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### process_data

- **Path:** `/TestProject/process_data/process_data`
- **Role:** `Sink`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| send_results -> process_data -> process_data | Unspecified | send_results -> process_data | in |
| valid_info_package | Unspecified | remove_duplicates | in |

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### read_info_packages

- **Path:** `/TestProject/read_info_packages`
- **Role:** `Transformer`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| config | Unspecified | read_config | in |

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| info_package_error | Unspecified | error_log | out |
| read_info_packages -> process_data | Unspecified | process_data | out |

#### Dependencies

| Instance | Library | Classification |
| --- | --- | --- |
| lib_logging -> read_infop | lib_logging | UsageImport |
| lib_soap_sm -> read_infop | lib_soap_sm | UsageImport |

#### Running and Compiling Instructions

_No instructions provided._

### send_results

- **Path:** `/TestProject/send_results`
- **Role:** `Generator`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

_None._

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| send_results -> process_data | Unspecified | process_data | out |
| send_results_error | Unspecified | error_log | out |

#### Dependencies

| Instance | Library | Classification |
| --- | --- | --- |
| lib_logging -> send_results | lib_logging | UsageImport |
| lib_soap_sm -> send_results | lib_soap_sm | UsageImport |

#### Running and Compiling Instructions

_No instructions provided._

### lib_io_file

- **Path:** `/TestProject/lib_io_file`
- **Role:** `ServiceLibrary`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

_None._

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### lib_soap_sm

- **Path:** `/TestProject/lib_soap_sm`
- **Role:** `ServiceLibrary`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

_None._

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### lib_logging

- **Path:** `/TestProject/lib_logging`
- **Role:** `ServiceLibrary`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

_None._

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### error_log

- **Path:** `/TestProject/error_log`
- **Role:** `ErrorHandler`
- **Direct language:** `javascript`
- **Direct technology:** _Not specified directly._

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

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### retry_failed_packages

- **Path:** `/TestProject/retry_failed_packages`
- **Role:** `Transformer`
- **Direct language:** _Not specified directly._
- **Direct technology:** _Not specified directly._

#### Inputs

| Name | Type | Source | Port |
| --- | --- | --- | --- |
| failed_info_package | Unspecified | error_log | in |

#### Outputs

| Name | Type | Target | Port |
| --- | --- | --- | --- |
| retry_failed_packages -> process_data | Unspecified | process_data | out |

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### config.json

- **Path:** `/TestProject/config.json`
- **Role:** `ProcessingUnit`
- **Direct language:** `json`
- **Direct technology:** _Not specified directly._

#### Inputs

_None._

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

### package.json

- **Path:** `/TestProject/package.json`
- **Role:** `ProcessingUnit`
- **Direct language:** `json`
- **Direct technology:** `nodejs`

#### Inputs

_None._

#### Outputs

_None._

#### Dependencies

_None._

#### Running and Compiling Instructions

_No instructions provided._

## Data Contracts

### config

- **Link ID:** `link_14`
- **From:** `read_config.out`
- **To:** `read_info_packages.in`
- **Transport:** `packet`
- **Classification:** `Transport`

_No payload type definition provided._

### config_error

- **Link ID:** `link_18`
- **From:** `read_config.out`
- **To:** `error_log.in`
- **Transport:** `error`
- **Classification:** `ErrorPipe`

_No payload type definition provided._

### failed_info_package

- **Link ID:** `link_23`
- **From:** `error_log.out`
- **To:** `retry_failed_packages.in`
- **Transport:** `packet`
- **Classification:** `Transport`

_No payload type definition provided._

### info_package_error

- **Link ID:** `link_19`
- **From:** `read_info_packages.out`
- **To:** `error_log.in`
- **Transport:** `error`
- **Classification:** `ErrorPipe`

_No payload type definition provided._

### lib_io_file -> read_config

- **Link ID:** `link_8`
- **From:** `lib_io_file.out`
- **To:** `read_config.in`
- **Transport:** `usage`
- **Classification:** `UsageImport`

_No payload type definition provided._

### lib_logging -> read_config

- **Link ID:** `link_10`
- **From:** `lib_logging.out`
- **To:** `read_config.in`
- **Transport:** `usage`
- **Classification:** `UsageImport`

_No payload type definition provided._

### lib_logging -> read_infop

- **Link ID:** `link_11`
- **From:** `lib_logging.out`
- **To:** `read_info_packages.in`
- **Transport:** `usage`
- **Classification:** `UsageImport`

_No payload type definition provided._

### lib_logging -> send_results

- **Link ID:** `link_12`
- **From:** `lib_logging.out`
- **To:** `send_results.in`
- **Transport:** `usage`
- **Classification:** `UsageImport`

_No payload type definition provided._

### lib_soap_sm -> read_infop

- **Link ID:** `link_9`
- **From:** `lib_soap_sm.out`
- **To:** `read_info_packages.in`
- **Transport:** `usage`
- **Classification:** `UsageImport`

_No payload type definition provided._

### lib_soap_sm -> send_results

- **Link ID:** `link_13`
- **From:** `lib_soap_sm.out`
- **To:** `send_results.in`
- **Transport:** `usage`
- **Classification:** `UsageImport`

_No payload type definition provided._

### read_info_packages -> process_data

- **Link ID:** `link_14d701b7-ae01-4f47-a887-a18f6b250f8e`
- **From:** `read_info_packages.out`
- **To:** `process_data.in`
- **Transport:** `in-process`
- **Classification:** `Transport`

_No payload type definition provided._

### retry_failed_packages -> process_data

- **Link ID:** `link_11b70edf-67a9-4080-a4e1-7e0613453d1c`
- **From:** `retry_failed_packages.out`
- **To:** `process_data.in`
- **Transport:** `in-process`
- **Classification:** `Transport`

_No payload type definition provided._

### send_results -> process_data

- **Link ID:** `link_afbf950c-3073-4552-8157-432a3aa2ad3a`
- **From:** `send_results.out`
- **To:** `process_data.in`
- **Transport:** `in-process`
- **Classification:** `Transport`

_No payload type definition provided._

### send_results_error

- **Link ID:** `link_21`
- **From:** `send_results.out`
- **To:** `error_log.in`
- **Transport:** `error`
- **Classification:** `ErrorPipe`

_No payload type definition provided._

### read_info_packages -> process_data -> remove_duplicates

- **Link ID:** `link_3d77721c-20e1-4c43-9770-d030d994e18e`
- **From:** `read_info_packages -> process_data.out`
- **To:** `remove_duplicates.in`
- **Transport:** `in-process`
- **Classification:** `Transport`

_No payload type definition provided._

### retry_failed_packages -> process_data -> remove_duplicates

- **Link ID:** `link_802d7da8-d815-4f59-b30d-e0e47ff93a36`
- **From:** `retry_failed_packages -> process_data.out`
- **To:** `remove_duplicates.in`
- **Transport:** `in-process`
- **Classification:** `Transport`

_No payload type definition provided._

### send_results -> process_data -> process_data

- **Link ID:** `link_5b65bb8d-20a1-487f-92bd-7cb17f92e78d`
- **From:** `send_results -> process_data.out`
- **To:** `process_data.in`
- **Transport:** `in-process`
- **Classification:** `Transport`

_No payload type definition provided._

### valid_info_package

- **Link ID:** `link_30`
- **From:** `remove_duplicates.out`
- **To:** `process_data.in`
- **Transport:** `packet`
- **Classification:** `Transport`

_No payload type definition provided._
