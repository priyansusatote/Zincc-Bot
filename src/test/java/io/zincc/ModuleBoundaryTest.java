package io.zincc;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ModuleBoundaryTest {

	@Test
	void featuresCannotCallTheLauncherOrFormDependencyCycles() {
		var production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("io.zincc");
		noClasses().that()
			.doNotHaveFullyQualifiedName(ZinccApplication.class.getName())
			.should()
			.dependOnClassesThat()
			.haveFullyQualifiedName(ZinccApplication.class.getName())
			.allowEmptyShould(true)
			.check(production);
		slices().matching("io.zincc.(*)..").should().beFreeOfCycles().allowEmptyShould(true).check(production);
	}

}
