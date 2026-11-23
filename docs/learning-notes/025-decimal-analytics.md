# P4.3 · Calculate from the extracted cells

**Implemented** a fixed decimal aggregate over immutable table rows with units, category and optional filter. A missing cell increases a counter. An explicit zero participates in the arithmetic. Recognized currency/unit columns prevent mixing or relabeling unlike quantities. The fixture confirms that 12.25 plus 4.75 equals 17.00, while -2.50 and a blank row produce 14.50 overall and one missing value.

[Feature, verification and limits](../modules/private-knowledge.md)
