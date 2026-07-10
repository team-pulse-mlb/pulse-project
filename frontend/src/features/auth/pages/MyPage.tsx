import { useEffect, useState } from 'react';

import { getMe, type MeResponse } from '../api/authApi';

function MyPage() {
    const [me, setMe] = useState<MeResponse | null>(null);

    useEffect(() => {
        const loadMe = async () => {
        const response = await getMe();

        setMe(response);
        };

        loadMe();
    }, []);

    return (
        <div>
        <h2>내 정보</h2>

        {me ? (
            <>
            <p>이메일: {me.email}</p>
            <p>권한: {me.roles.join(', ')}</p>
            </>
        ) : (
            <p>사용자 정보를 불러오는 중입니다...</p>
        )}
        </div>
    );
}

export default MyPage;