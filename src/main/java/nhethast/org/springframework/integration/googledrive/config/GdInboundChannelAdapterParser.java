/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nhethast.org.springframework.integration.googledrive.config;

import org.springframework.integration.file.config.AbstractRemoteFileInboundChannelAdapterParser;

import nhethast.org.springframework.integration.googledrive.filters.GdRegexPatternFileListFilter;
import nhethast.org.springframework.integration.googledrive.filters.GdSimplePatternFileListFilter;
import nhethast.org.springframework.integration.googledrive.inbound.GdInboundFileSynchronizer;
import nhethast.org.springframework.integration.googledrive.inbound.GdInboundFileSynchronizingMessageSource;

public class GdInboundChannelAdapterParser extends AbstractRemoteFileInboundChannelAdapterParser {

	@Override
	protected String getMessageSourceClassname() {
		return GdInboundFileSynchronizingMessageSource.class.getName();
	}

	@Override
	protected String getInboundFileSynchronizerClassname() {
		return GdInboundFileSynchronizer.class.getName();
	}

	@Override
	protected String getSimplePatternFileListFilterClassname() {
		return GdSimplePatternFileListFilter.class.getName();
	}

	@Override
	protected String getRegexPatternFileListFilterClassname() {
		return GdRegexPatternFileListFilter.class.getName();
	}

}
