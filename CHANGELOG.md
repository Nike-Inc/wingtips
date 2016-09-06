# Wingtips Changelog / Release Notes

All notable changes to `Wingtips` will be documented in this file. `Wingtips` adheres to [Semantic Versioning](http://semver.org/).

## Why pre-1.0 releases?

Wingtips is used heavily and is stable internally at Nike, however the wider community may have needs or use cases that we haven't considered. Therefore Wingtips will live at a sub-1.0 version for a short time after its initial open source release to give it time to respond quickly to the open source community without ballooning the version numbers. Once its public APIs have stabilized again as an open source project it will be switched to the normal post-1.0 semantic versioning system.

#### 0.x Releases

- `0.11.x` Releases - [0.11.1](#0111), [0.11.0](#0110)
- `0.10.x` Releases - [0.10.0](#0100)
- `0.9.x` Releases - [0.9.0.1](#0901), [0.9.0](#090)

## [0.11.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.11.1)

Released on 2016-09-05.

### Updated

- Zipkin updated to version 1.8.4.
	- Updated by [Adrian Cole][contrib_adriancole] in pull request [#24](https://github.com/Nike-Inc/wingtips/pull/24).

## [0.11.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.11.0)

Released on 2016-08-20.

### Fixed

- Trace and span ID generation changed to conform to Zipkin/B3 standards by encoding IDs in lowercase hexadecimal format.
	- Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#16](https://github.com/Nike-Inc/wingtips/pull/16). For issues [#14](https://github.com/Nike-Inc/wingtips/issues/14) and [#15](https://github.com/Nike-Inc/wingtips/issues/15).

## [0.10.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.10.0)

Released on 2016-08-11.

### Added

- Zipkin integration.
	- Added by [Nic Munroe][contrib_nicmunroe] in pull requests [#7](https://github.com/Nike-Inc/wingtips/pull/7), [#8](https://github.com/Nike-Inc/wingtips/pull/8), and [#10](https://github.com/Nike-Inc/wingtips/pull/10). For issue [#9](https://github.com/Nike-Inc/wingtips/issues/9).

## [0.9.0.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.9.0.1)

Released on 2016-07-19.

### Added

- Javadoc jar, CONTRIBUTING.md doc, Travis CI badge in readme, Download badge in readme, artifact publishing support.
	- Added by [Nic Munroe][contrib_nicmunroe].

### Fixed

- Broken Zipkin link.
	- Fixed by [Adrian Cole][contrib_adriancole].

## [0.9.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.9.0)

Released on 2016-06-07.

### Added

- Initial open source code drop for Wingtips.
	- Added by [Nic Munroe][contrib_nicmunroe].
	

[contrib_nicmunroe]: https://github.com/nicmunroe
[contrib_adriancole]: https://github.com/adriancole