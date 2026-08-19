import { Helmet } from 'react-helmet-async';

export default function SEO({ title, description }) {
    const siteTitle = 'GALAXAM SIRH';
    const fullTitle = title ? `${title} | ${siteTitle}` : siteTitle;

    return (
        <Helmet>
            <title>{fullTitle}</title>
            {description && <meta name="description" content={description} />}
            <meta name="robots" content="noindex, nofollow" />
        </Helmet>
    );
}
