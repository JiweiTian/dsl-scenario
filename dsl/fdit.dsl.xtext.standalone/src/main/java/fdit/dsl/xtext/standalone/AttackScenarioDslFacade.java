package fdit.dsl.xtext.standalone;

import com.google.inject.Injector;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.diagnostics.AbstractDiagnostic;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.TextRegion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static fdit.tools.stream.StreamUtils.filter;
import static fdit.tools.stream.StreamUtils.mapping;
import static org.eclipse.emf.common.util.URI.createURI;

public class AttackScenarioDslFacade implements DslFacade {

    private /*final*/ XtextResourceSet xtextResourceSet;
    private /*final*/ XtextResource xtextResource;
    private /*final*/ ContentAssistService contentAssistService;
    private /*final*/ ISemanticHighlightingCalculator semanticHighlightingCalculator;

    private String currentAttackScenario;

    @Override
    public void initialize() {
        final Injector injector = new ExtendedAttackScenarioStandaloneSetup().createInjectorAndDoEMFRegistration();
        xtextResourceSet = injector.getInstance(XtextResourceSet.class);
        xtextResourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        xtextResource = (XtextResource) xtextResourceSet.createResource(createURI("fdit:/dslfacade.scenario"));
        contentAssistService = injector.getInstance(ContentAssistService.class);
        semanticHighlightingCalculator = injector.getInstance(ISemanticHighlightingCalculator.class);
    }

    @Override
    public void shutdown() {
        contentAssistService.shutdown();
    }

    @Override
    public void parse(final String attackScenario) throws IOException {
        currentAttackScenario = attackScenario;
        try (final InputStream scenarioInputStream = new ByteArrayInputStream(attackScenario.getBytes())) {
            if (xtextResource.isLoaded()) {
                xtextResource.unload();
            }
            xtextResource.load(scenarioInputStream, xtextResourceSet.getLoadOptions());
        }
    }

    public EList<EObject> getAST(final String attackScenario) throws IOException {
        final XtextResource xtextResource = (XtextResource) xtextResourceSet.createResource(createURI("fdit:/dslfacade.scenario"));
        try (final InputStream scenarioInputStream = new ByteArrayInputStream(attackScenario.getBytes())) {
            if (xtextResource.isLoaded()) {
                xtextResource.unload();
            }
            xtextResource.load(scenarioInputStream, xtextResourceSet.getLoadOptions());
        }
        return xtextResource.getContents();
    }

    public EList<EObject> getAST() {
        if (xtextResource == null) {
            return new BasicEList<>();
        }
        return xtextResource.getContents();
    }

    @Override
    public Collection<SyntaxFault> getParseErrors() {
        return mapping(filter(xtextResource.getErrors(), AbstractDiagnostic.class),
                diagnostic -> new SyntaxFault(diagnostic.getOffset(), diagnostic.getLine(), diagnostic.getColumn(),
                        diagnostic.getLength(), diagnostic.getMessage()));
    }

    @Override
    public Collection<CompletionProposal> getProposals(final int caretOffset,
                                                       final int selectionLength,
                                                       final int limit) {
        return contentAssistService.createProposals(xtextResource,
                currentAttackScenario,
                new TextRegion(caretOffset, selectionLength),
                caretOffset,
                limit);
    }

    @Override
    public Collection<StylesPosition> getHighlightingStyles() {
        final Collection<StylesPosition> stylesPositions = newArrayList();
        semanticHighlightingCalculator.provideHighlightingFor(xtextResource,
                (offset, length, id) -> stylesPositions.add(new StylesPosition(offset, length, newArrayList(id))),
                CancelIndicator.NullImpl);
        return stylesPositions;
    }

    public Optional<EObject> getCurrentNode(final int caretOffset,
                                            final int selectionLength) {
        final ContentAssistContext[] contentAssistContexts =
                contentAssistService.getContexts(
                        xtextResource,
                        currentAttackScenario,
                        new TextRegion(caretOffset, selectionLength),
                        caretOffset);
        if (contentAssistContexts.length > 0) {
            return Optional.ofNullable(contentAssistContexts[0].getCurrentModel());
        }
        return Optional.empty();
    }
}