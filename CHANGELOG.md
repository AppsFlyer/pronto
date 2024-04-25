## This library follows [Semantic Versioning](https://semver.org).
## This CHANGELOG follows [keepachangelog](https://keepachangelog.com/en/1.0.0/).

###  VERSION [3.0.0]:
#### Changed
* Minimal supported protobuf version 3.15.0.
* when a getting an optional field that was not set, a nil will be returned instead of the deafault value of this field.
* Allow assoc a nil to optional fields - It will clear the field.
* Allow checking if an optional field was set or not with `p/has-field?`.

###  VERSION [2.1.2]:
#### Changed
* return `:unrecognized` for unknown enum values in order to support forward compatibility.

#### Added
* CHANGELOG.md file  
