/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.dsltext.backend.server;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;

import org.drools.compiler.lang.dsl.DSLMappingParseException;
import org.drools.compiler.lang.dsl.DSLTokenizedMappingFile;
import org.drools.workbench.screens.dsltext.service.DSLTextEditorService;
import org.drools.workbench.screens.dsltext.type.DSLResourceTypeDefinition;
import org.guvnor.common.services.backend.exceptions.ExceptionUtilities;
import org.guvnor.common.services.project.builder.events.InvalidateDMOPackageCacheEvent;
import org.guvnor.common.services.shared.file.CopyService;
import org.guvnor.common.services.shared.file.DeleteService;
import org.guvnor.common.services.shared.file.RenameService;
import org.guvnor.common.services.shared.metadata.MetadataService;
import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.jboss.errai.bus.server.annotations.Service;
import org.kie.commons.io.IOService;
import org.kie.commons.java.nio.base.options.CommentedOption;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.backend.vfs.Path;
import org.uberfire.security.Identity;
import org.uberfire.workbench.events.ResourceAddedEvent;
import org.uberfire.workbench.events.ResourceOpenedEvent;
import org.uberfire.workbench.events.ResourceUpdatedEvent;

@Service
@ApplicationScoped
public class DSLTextEditorServiceImpl implements DSLTextEditorService {

    @Inject
    @Named("ioStrategy")
    private IOService ioService;

    @Inject
    private MetadataService metadataService;

    @Inject
    private CopyService copyService;

    @Inject
    private DeleteService deleteService;

    @Inject
    private RenameService renameService;

    @Inject
    private Event<InvalidateDMOPackageCacheEvent> invalidateDMOPackageCache;

    @Inject
    private Event<ResourceOpenedEvent> resourceOpenedEvent;

    @Inject
    private Event<ResourceAddedEvent> resourceAddedEvent;

    @Inject
    private Event<ResourceUpdatedEvent> resourceUpdatedEvent;

    @Inject
    private Paths paths;

    @Inject
    private Identity identity;

    @Inject
    private DSLResourceTypeDefinition resourceTypeDefinition;

    @Override
    public Path create( final Path context,
                        final String fileName,
                        final String content,
                        final String comment ) {
        try {
            final org.kie.commons.java.nio.file.Path nioPath = paths.convert( context ).resolve( fileName );
            final Path newPath = paths.convert( nioPath,
                                                false );

            ioService.createFile( nioPath );
            ioService.write( nioPath,
                             content,
                             makeCommentedOption( comment ) );

            //Signal creation to interested parties
            resourceAddedEvent.fire( new ResourceAddedEvent( newPath ) );

            return newPath;

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public String load( final Path path ) {
        try {
            final String content = ioService.readAllString( paths.convert( path ) );

            //Signal opening to interested parties
            resourceOpenedEvent.fire( new ResourceOpenedEvent( path ) );

            return content;

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public Path save( final Path resource,
                      final String content,
                      final Metadata metadata,
                      final String comment ) {
        try {
            ioService.write( paths.convert( resource ),
                             content,
                             metadataService.setUpAttributes( resource,
                                                              metadata ),
                             makeCommentedOption( comment ) );

            //Invalidate Package-level DMO cache as a DSL has been altered
            invalidateDMOPackageCache.fire( new InvalidateDMOPackageCacheEvent( resource ) );

            //Signal update to interested parties
            resourceUpdatedEvent.fire( new ResourceUpdatedEvent( resource ) );

            return resource;

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public void delete( final Path path,
                        final String comment ) {
        try {
            deleteService.delete( path,
                                  comment );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public Path rename( final Path path,
                        final String newName,
                        final String comment ) {
        try {
            return renameService.rename( path,
                                         newName,
                                         comment );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public Path copy( final Path path,
                      final String newName,
                      final String comment ) {
        try {
            return copyService.copy( path,
                                     newName,
                                     comment );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public boolean accepts( final Path path ) {
        return resourceTypeDefinition.accept( path );
    }

    @Override
    public List<ValidationMessage> validate( final Path path ) {
        try {
            final String content = ioService.readAllString( paths.convert( path ) );
            return validate( path,
                             content );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public List<ValidationMessage> validate( final Path path,
                                             final String content ) {
        return doValidation( content );
    }

    private List<ValidationMessage> doValidation( final String content ) {
        final List<ValidationMessage> validationMessages = new ArrayList<ValidationMessage>();
        final DSLTokenizedMappingFile dslLoader = new DSLTokenizedMappingFile();
        try {
            if ( !dslLoader.parseAndLoad( new StringReader( content ) ) ) {
                validationMessages.addAll( makeValidationMessages( dslLoader ) );
            }
            return validationMessages;

        } catch ( IOException e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    private List<ValidationMessage> makeValidationMessages( final DSLTokenizedMappingFile dslLoader ) {
        final List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
        for ( final Object o : dslLoader.getErrors() ) {
            if ( o instanceof DSLMappingParseException ) {
                final DSLMappingParseException dslMappingParseException = (DSLMappingParseException) o;
                messages.add( makeNewValidationMessage( dslMappingParseException ) );
            } else if ( o instanceof Exception ) {
                final Exception e = (Exception) o;
                messages.add( makeNewValidationMessage( e ) );
            } else {
                messages.add( makeNewValidationMessage( o ) );
            }
        }
        return messages;
    }

    private ValidationMessage makeNewValidationMessage( final DSLMappingParseException e ) {
        final ValidationMessage msg = new ValidationMessage();
        msg.setLevel( ValidationMessage.Level.ERROR );
        msg.setLine( e.getLine() );
        msg.setText( e.getMessage() );
        return msg;
    }

    private ValidationMessage makeNewValidationMessage( final Exception e ) {
        final ValidationMessage msg = new ValidationMessage();
        msg.setLevel( ValidationMessage.Level.ERROR );
        msg.setText( "Exception " + e.getClass() + " " + e.getMessage() + " " + e.getCause() );
        return msg;
    }

    private ValidationMessage makeNewValidationMessage( final Object o ) {
        final ValidationMessage msg = new ValidationMessage();
        msg.setLevel( ValidationMessage.Level.ERROR );
        msg.setText( "Uncategorized error " + o );
        return msg;
    }

    private CommentedOption makeCommentedOption( final String commitMessage ) {
        final String name = identity.getName();
        final Date when = new Date();
        final CommentedOption co = new CommentedOption( name,
                                                        null,
                                                        commitMessage,
                                                        when );
        return co;
    }
}
